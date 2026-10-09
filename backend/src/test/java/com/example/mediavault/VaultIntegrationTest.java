package com.example.mediavault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={
    "spring.datasource.url=${DB_TEST_URL}","spring.datasource.username=${DB_TEST_USERNAME}",
    "spring.datasource.password=${DB_TEST_PASSWORD}",
    "vault.bootstrap.username=test-admin","vault.bootstrap.password=Test-admin-2026!",
    "vault.legacy-writes-enabled=true"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".*media_vault_test(?:\\?.*)?$")
class VaultIntegrationTest {
    private static final String PASSWORD="Test-admin-2026!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired com.example.mediavault.security.UserService users;
    MockHttpSession admin;

    @BeforeEach void resetOnlyTestDatabase() throws Exception {
        TestDatabase.resetCatalog(jdbc);
        jdbc.update("DELETE FROM app_user WHERE normalized_username<>'test-admin'");
        jdbc.update("UPDATE app_user SET role='ADMIN',enabled=TRUE,password_hash=?,version=0,security_version=security_version+1 WHERE normalized_username='test-admin'",encoder.encode(PASSWORD));
        admin=login("test-admin",PASSWORD);
    }

    @Test void authenticationAndCsrfAreEnforcedSeparately() throws Exception {
        mvc.perform(get("/api/assets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/tags").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cos\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","test-admin").param("password","wrong"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test void bootstrapDoesNotOverwriteExistingUsersAndNormalizedNamesAreValidated() throws Exception {
        String before=jdbc.queryForObject("SELECT password_hash FROM app_user WHERE normalized_username='test-admin'",String.class);
        users.bootstrap("overwritten-admin","Different-password-2026!");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE normalized_username='test-admin'",String.class)).isEqualTo(before);
        request(post("/api/admin/users"),admin,Map.of("username","İ".repeat(64),"password",PASSWORD)).andExpect(status().isBadRequest());
        JsonNode viewer=createUser("Default-role-check","VIEWER");
        assertThat(viewer.path("role").asText()).isEqualTo("VIEWER");
    }

    @Test void concurrentRootTagCreationHasOneWinner() throws Exception {
        MockHttpSession other=login("test-admin",PASSWORD);
        int[] statuses=parallel(
            ()->request(post("/api/tags"),admin,Map.of("name","Cos")).andReturn().getResponse().getStatus(),
            ()->request(post("/api/tags"),other,Map.of("name","COS")).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tag",Long.class)).isEqualTo(1);
    }

    @Test void viewerReadsButCannotWriteOrEscalateAndCanChangeOwnPassword() throws Exception {
        JsonNode viewer=createUser("viewer","VIEWER");MockHttpSession session=login("viewer",PASSWORD);
        mvc.perform(get("/api/assets").session(session)).andExpect(status().isOk());
        request(post("/api/tags"),session,Map.of("name","forbidden")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        request(put("/api/auth/password"),session,Map.of("oldPassword",PASSWORD,"newPassword","Changed-password-2026!","role","ADMIN"))
            .andExpect(status().isBadRequest());
        request(put("/api/auth/password"),session,Map.of("oldPassword",PASSWORD,"newPassword","Changed-password-2026!"))
            .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?",String.class,viewer.path("id").asText())).isEqualTo("VIEWER");
        login("viewer","Changed-password-2026!");
    }

    @Test void threeLevelsAndMultipleBranchesFilterWithoutDuplicates() throws Exception {
        JsonNode cos=tag("Cos",null), role=tag("公孙离",cos.path("id").asText()),skin=tag("七零雪竹",role.path("id").asText());
        JsonNode style=tag("风格",null), ancient=tag("古风",style.path("id").asText()), daily=tag("日常",style.path("id").asText());
        JsonNode asset=asset("100%_! 示例",List.of(cos.path("id").asText(),skin.path("id").asText(),ancient.path("id").asText()));
        assertThat(asset.path("tags").toString()).contains("Cos / 公孙离 / 七零雪竹");
        mvc.perform(get("/api/assets").session(admin).param("tagIds",cos.path("id").asText()+","+skin.path("id").asText()+","+ancient.path("id").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/assets").session(admin).param("tagIds",skin.path("id").asText()+","+daily.path("id").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/assets").session(admin).param("q","%_!"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/assets").session(admin).param("tagIds","999999999"))
            .andExpect(status().isBadRequest());
    }

    @Test void tagSearchMatchesNamesAndParentPathsAndDistinguishesSameNamedBranches() throws Exception {
        JsonNode cos = tag("Cos", null);
        JsonNode cosRole = tag("公孙离", cos.path("id").asText());
        tag("示例皮肤", cosRole.path("id").asText());
        JsonNode characters = tag("游戏角色", null);
        tag("公孙离", characters.path("id").asText());

        mvc.perform(get("/api/tags").session(admin).param("q", "孙离"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items[0].path").value("Cos / 公孙离"))
            .andExpect(jsonPath("$.items[1].path").value("Cos / 公孙离 / 示例皮肤"))
            .andExpect(jsonPath("$.items[2].path").value("游戏角色 / 公孙离"));
        mvc.perform(get("/api/tags").session(admin).param("q", " cOs "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items[0].path").value("Cos"))
            .andExpect(jsonPath("$.items[2].path").value("Cos / 公孙离 / 示例皮肤"));
        mvc.perform(get("/api/tags").session(admin).param("q", "\t cOs / 公孙离 \u2003"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].path").value("Cos / 公孙离"))
            .andExpect(jsonPath("$.items[1].path").value("Cos / 公孙离 / 示例皮肤"))
            .andExpect(jsonPath("$.items[0].children.length()").value(0));
    }

    @Test void tagSearchNormalizesUnicodeAndTreatsWildcardsLiterallyWithEmptyAndMissingResults() throws Exception {
        JsonNode cafe = tag("Café", null);
        tag("100%_原样", cafe.path("id").asText());
        JsonNode other = tag("Other", null);
        tag("普通标签", other.path("id").asText());

        mvc.perform(get("/api/tags").session(admin).param("q", "\u2003 CAFE\u0301 \u2003"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].path").value("Café"))
            .andExpect(jsonPath("$.items[1].path").value("Café / 100%_原样"));
        mvc.perform(get("/api/tags").session(admin).param("q", "\u00A0\uFEFF\u2028 CAFE\u0301 \u2029\uFEFF\u00A0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].path").value("Café"))
            .andExpect(jsonPath("$.items[1].path").value("Café / 100%_原样"));
        mvc.perform(get("/api/tags").session(admin).param("q", "%_"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].name").value("100%_原样"));
        mvc.perform(get("/api/tags").session(admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4))
            .andExpect(jsonPath("$.items.length()").value(4));
        mvc.perform(get("/api/tags").session(admin).param("q", "\t \u2003"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4))
            .andExpect(jsonPath("$.items.length()").value(4));
        mvc.perform(get("/api/tags").session(admin).param("q", "\u00A0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4))
            .andExpect(jsonPath("$.items.length()").value(4));
        mvc.perform(get("/api/tags").session(admin).param("q", "\u001CCafé"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/tags").session(admin).param("q", "不存在的路径"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(20));
    }

    @Test void tagSearchPaginatesFilteredPathsWithStableTotalsAndAnEmptyOverflowPage() throws Exception {
        JsonNode cos = tag("Cos", null);
        for (String child : List.of("A", "B", "C", "D")) tag(child, cos.path("id").asText());
        JsonNode other = tag("Other", null);
        tag("Unrelated", other.path("id").asText());

        mvc.perform(get("/api/tags").session(admin).param("q", "COS").param("page", "1").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].path").value("Cos"))
            .andExpect(jsonPath("$.items[1].path").value("Cos / A"));
        mvc.perform(get("/api/tags").session(admin).param("q", "COS").param("page", "2").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.items[0].path").value("Cos / B"))
            .andExpect(jsonPath("$.items[1].path").value("Cos / C"));
        mvc.perform(get("/api/tags").session(admin).param("q", "COS").param("page", "3").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].path").value("Cos / D"));
        mvc.perform(get("/api/tags").session(admin).param("q", "COS").param("page", "4").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test void tagMoveChangesAncestryAndDeleteKeepsAssetWithVersionConflict() throws Exception {
        JsonNode cos=tag("Cos",null),other=tag("Other",null),leaf=tag("Leaf",cos.path("id").asText());
        JsonNode asset=asset("Example",List.of(leaf.path("id").asText()));
        request(put("/api/tags/"+leaf.path("id").asText()),admin,Map.of("name","Leaf","parentId",other.path("id").asText(),"version",0))
            .andExpect(status().isOk());
        mvc.perform(get("/api/assets").session(admin).param("tagIds",cos.path("id").asText())).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/assets").session(admin).param("tagIds",other.path("id").asText())).andExpect(jsonPath("$.total").value(1));
        mvc.perform(delete("/api/tags/"+other.path("id").asText()).param("version","0").session(admin).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/tags/"+leaf.path("id").asText()).param("version","1").session(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/"+asset.path("id").asText()).session(admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.tags.length()").value(0)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete("/api/assets/"+asset.path("id").asText()).param("version","0").session(admin).with(csrf())).andExpect(status().isConflict());
    }

    @Test void siblingUniquenessDepthAndCyclesAreProtected() throws Exception {
        JsonNode first=tag("Cos",null);
        request(post("/api/tags"),admin,Map.of("name"," COS ")).andExpect(status().isConflict());
        JsonNode second=tag("Other",null);tag("Same",first.path("id").asText());tag("Same",second.path("id").asText());
        String parent=first.path("id").asText();
        for(int i=2;i<=8;i++) parent=tag("Depth-"+i,parent).path("id").asText();
        request(post("/api/tags"),admin,Map.of("name","Depth-9","parentId",parent)).andExpect(status().isBadRequest());
        request(put("/api/tags/"+first.path("id").asText()),admin,Map.of("name","Cos","parentId",parent,"version",0)).andExpect(status().isBadRequest());
    }

    @Test void sourcesLocationsAndOptimisticLockPreserveRecords() throws Exception {
        JsonNode source=body(request(post("/api/sources"),admin,Map.of("name","Disk A","type","LOCAL_DISK")).andExpect(status().isCreated()).andReturn());
        JsonNode asset=body(request(post("/api/assets"),admin,Map.of("title","Sample","kind","VIDEO","tagIds",List.of(),"locations",List.of(
            Map.of("sourceId",source.path("id").asText(),"locatorType","LOCAL_PATH","locator","D:/example/video.mp4"))))
            .andExpect(status().isCreated()).andReturn());
        mvc.perform(delete("/api/sources/"+source.path("id").asText()).param("version","0").session(admin).with(csrf())).andExpect(status().isConflict());
        request(put("/api/sources/"+source.path("id").asText()+"/reported-status"),admin,Map.of("status","OFFLINE","version",0)).andExpect(status().isOk());
        mvc.perform(get("/api/assets/"+asset.path("id").asText()).session(admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.locations[0].reportedStatus").value("OFFLINE"));
        request(put("/api/assets/"+asset.path("id").asText()),admin,Map.of("title","Updated","kind","VIDEO","tagIds",List.of(),"version",0)).andExpect(status().isOk());
        request(put("/api/assets/"+asset.path("id").asText()),admin,Map.of("title","Stale","kind","VIDEO","tagIds",List.of(),"version",0)).andExpect(status().isConflict());
        mvc.perform(delete("/api/assets/"+asset.path("id").asText()).param("version","1").session(admin).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_location",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storage_source",Long.class)).isEqualTo(1);
    }

    @Test void demotionDisableAndPasswordResetInvalidateExistingSessions() throws Exception {
        JsonNode user=createUser("second-admin","ADMIN");MockHttpSession old=login("second-admin",PASSWORD);
        request(put("/api/admin/users/"+user.path("id").asText()+"/role"),admin,Map.of("role","VIEWER","version",0)).andExpect(status().isOk());
        mvc.perform(get("/api/assets").session(old)).andExpect(status().isUnauthorized());
        MockHttpSession viewer=login("second-admin",PASSWORD);
        request(post("/api/admin/users/"+user.path("id").asText()+"/reset-password"),admin,Map.of("password","Reset-password-2026!","version",1)).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets").session(viewer)).andExpect(status().isUnauthorized());
        MockHttpSession reset=login("second-admin","Reset-password-2026!");
        request(put("/api/admin/users/"+user.path("id").asText()+"/status"),admin,Map.of("enabled",false,"version",2)).andExpect(status().isOk());
        mvc.perform(get("/api/assets").session(reset)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","second-admin").param("password","Reset-password-2026!"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").session(admin)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password_hash"))));
    }

    @Test void concurrentTagMovesCannotCreateCycle() throws Exception {
        JsonNode a=tag("A",null),b=tag("B",null);MockHttpSession other=login("test-admin",PASSWORD);
        int[] statuses=parallel(
            ()->request(put("/api/tags/"+a.path("id").asText()),admin,Map.of("name","A","parentId",b.path("id").asText(),"version",0)).andReturn().getResponse().getStatus(),
            ()->request(put("/api/tags/"+b.path("id").asText()),other,Map.of("name","B","parentId",a.path("id").asText(),"version",0)).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200,400);
        mvc.perform(get("/api/tags/tree").session(admin)).andExpect(status().isOk());
    }

    @Test void concurrentAdminDemotionsPreserveOneEnabledAdmin() throws Exception {
        JsonNode second=createUser("second-admin","ADMIN");MockHttpSession other=login("second-admin",PASSWORD);
        String firstId=jdbc.queryForObject("SELECT id FROM app_user WHERE normalized_username='test-admin'",String.class);
        int[] statuses=parallel(
            ()->request(put("/api/admin/users/"+firstId+"/role"),admin,Map.of("role","VIEWER","version",0)).andReturn().getResponse().getStatus(),
            ()->request(put("/api/admin/users/"+second.path("id").asText()+"/role"),other,Map.of("role","VIEWER","version",0)).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role='ADMIN' AND enabled=TRUE",Long.class)).isEqualTo(1);
    }

    private MockHttpSession login(String username,String password) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",username).param("password",password))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private JsonNode tag(String name,String parent) throws Exception {
        var b=new java.util.HashMap<String,Object>();b.put("name",name);b.put("parentId",parent);
        return body(request(post("/api/tags"),admin,b).andExpect(status().isCreated()).andReturn());
    }
    private JsonNode asset(String title,List<String> tags) throws Exception {
        return body(request(post("/api/assets"),admin,Map.of("title",title,"kind","IMAGE_SET","tagIds",tags)).andExpect(status().isCreated()).andReturn());
    }
    private JsonNode createUser(String name,String role) throws Exception {
        return body(request(post("/api/admin/users"),admin,Map.of("username",name,"password",PASSWORD,"role",role)).andExpect(status().isCreated()).andReturn());
    }
    private org.springframework.test.web.servlet.ResultActions request(MockHttpServletRequestBuilder b,MockHttpSession session,Object body) throws Exception {
        return mvc.perform(b.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private JsonNode body(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
    private interface Checked { int get() throws Exception; }
    private int[] parallel(Checked a,Checked b) throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start=new CountDownLatch(1);
            var fa=pool.submit(()->{start.await();return a.get();});var fb=pool.submit(()->{start.await();return b.get();});start.countDown();
            return new int[]{fa.get(15,TimeUnit.SECONDS),fb.get(15,TimeUnit.SECONDS)};
        }
    }
}
