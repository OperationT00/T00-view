package com.example.mediavault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
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

@SpringBootTest(properties={"spring.datasource.url=${DB_TEST_URL}","spring.datasource.username=${DB_TEST_USERNAME}","spring.datasource.password=${DB_TEST_PASSWORD}","vault.bootstrap.username=test-admin","vault.bootstrap.password=Test-admin-2026!"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".*media_vault_test(?:\\?.*)?$")
class LegacyMigrationIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder encoder;
    MockHttpSession admin;
    @BeforeEach void setup() throws Exception {
        TestDatabase.resetCatalog(jdbc);
        jdbc.update("DELETE FROM app_user WHERE normalized_username<>'test-admin'");
        jdbc.update("UPDATE app_user SET enabled=TRUE,role='ADMIN',password_hash=?,version=0,security_version=security_version+1 WHERE normalized_username='test-admin'",encoder.encode("Test-admin-2026!"));
        admin=login("test-admin","Test-admin-2026!");
    }
    @Test void confirmedMappingsPreserveAncestorMeaningAndOldDataAndAreIdempotent() throws Exception {
        String root=oldTag("Cos",null),role=oldTag("公孙离",root),leaf=oldTag("七零雪竹",role),asset=oldAsset("旧收藏",leaf);
        JsonNode type=entity("types",Map.of("name","cos"));JsonNode work=entity("work-scopes",Map.of("name","示例原作"));
        JsonNode character=entity("characters",Map.of("name","公孙离","workScopeId",work.path("id").asText()));
        JsonNode skin=entity("skins",Map.of("name","七零雪竹","characterId",character.path("id").asText()));
        mapping(root,"TYPE",type);mapping(role,"CHARACTER",character);mapping(leaf,"SKIN",skin);
        JsonNode plan=body(write(post("/api/v2/legacy-migration/preview"),Map.of()).andExpect(status().isOk()).andReturn());
        assertThat(plan.path("assets").get(0).path("additions").path("TYPE").size()).isEqualTo(1);
        assertThat(plan.path("assets").get(0).path("unmappedTags").size()).isZero();
        write(post("/api/v2/legacy-migration/apply"),Map.of("token",plan.path("token").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.updatedAssets").value(1));
        mvc.perform(get("/api/v2/assets/"+asset).session(admin)).andExpect(jsonPath("$.tags.length()").value(1)).andExpect(jsonPath("$.directFacets.TYPE.length()").value(1)).andExpect(jsonPath("$.effectiveFacets.WORK.length()").value(1)).andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tag",Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_tag",Long.class)).isEqualTo(1);
        write(post("/api/v2/legacy-migration/apply"),Map.of("token",plan.path("token").asText())).andExpect(status().isConflict());
        JsonNode again=body(write(post("/api/v2/legacy-migration/preview"),Map.of()).andExpect(status().isOk()).andReturn());
        write(post("/api/v2/legacy-migration/apply"),Map.of("token",again.path("token").asText())).andExpect(jsonPath("$.updatedAssets").value(0));
    }
    @Test void previewRejectsAssetOrMappingChangesAndTokensAreBoundToSession() throws Exception {
        String legacy=oldTag("旧类别",null),asset=oldAsset("待迁移",legacy);JsonNode type=entity("types",Map.of("name","cos"));mapping(legacy,"TYPE",type);
        JsonNode preview=body(write(post("/api/v2/legacy-migration/preview"),Map.of()).andReturn());
        MockHttpSession other=login("test-admin","Test-admin-2026!");
        mvc.perform(post("/api/v2/legacy-migration/apply").session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",preview.path("token").asText())))).andExpect(status().isConflict());
        jdbc.update("UPDATE asset SET version=version+1 WHERE id=?",Long.parseLong(asset));
        write(post("/api/v2/legacy-migration/apply"),Map.of("token",preview.path("token").asText())).andExpect(status().isConflict());
        JsonNode next=body(write(post("/api/v2/legacy-migration/preview"),Map.of()).andReturn());
        write(put("/api/v2/legacy-migration/mappings/"+legacy),Map.of("targets",List.of())).andExpect(status().isOk());
        write(post("/api/v2/legacy-migration/apply"),Map.of("token",next.path("token").asText())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_content_type",Long.class)).isZero();
    }
    @Test void unmappedLabelsRemainVisibleAndAreNotGuessedFromNames() throws Exception {
        String legacy=oldTag("公孙离",null),asset=oldAsset("原始收藏",legacy);
        entity("characters",Map.of("name","公孙离"));
        JsonNode preview=body(write(post("/api/v2/legacy-migration/preview"),Map.of()).andReturn());
        assertThat(preview.path("assets").get(0).path("unmappedTags").get(0).path("name").asText()).isEqualTo("公孙离");
        write(post("/api/v2/legacy-migration/apply"),Map.of("token",preview.path("token").asText())).andExpect(jsonPath("$.updatedAssets").value(0));
        mvc.perform(get("/api/v2/assets/"+asset).session(admin)).andExpect(jsonPath("$.directFacets.CHARACTER.length()").value(0)).andExpect(jsonPath("$.tags.length()").value(1));
    }
    @Test void mappingReferencesProtectEntitiesAndDisabledTargetsCannotBeNewlyApplied() throws Exception {
        String legacy=oldTag("旧类别",null);oldAsset("收藏",legacy);JsonNode type=entity("types",Map.of("name","cos"));mapping(legacy,"TYPE",type);
        mvc.perform(delete("/api/v2/catalog/types/"+type.path("id").asText()).param("version","0").session(admin).with(csrf())).andExpect(status().isConflict());
        write(put("/api/v2/catalog/types/"+type.path("id").asText()),Map.of("name","cos","enabled",false,"version",0)).andExpect(status().isOk());
        write(post("/api/v2/legacy-migration/preview"),Map.of()).andExpect(status().isBadRequest());
    }
    @Test void newEndpointsRequireLoginCsrfAndAdminAndOldWritesAreReadOnly() throws Exception {
        mvc.perform(get("/api/v2/catalog/search")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v2/catalog/types").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"cos\"}")).andExpect(status().isForbidden());
        JsonNode viewer=body(write(post("/api/admin/users"),Map.of("username","migration-viewer","password","Viewer-test-2026!","role","VIEWER")).andReturn());assertThat(viewer.path("id").asText()).isNotEmpty();
        MockHttpSession session=login("migration-viewer","Viewer-test-2026!");
        mvc.perform(get("/api/v2/catalog/search").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v2/legacy-migration").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v2/assets").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        write(post("/api/tags"),Map.of("name","旧写入")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEGACY_READ_ONLY"));
        write(post("/api/assets"),Map.of("title","旧写入","kind","VIDEO")).andExpect(status().isConflict());
        mvc.perform(get("/api/v2/catalog/unknown").session(admin)).andExpect(status().isForbidden());
    }
    private MockHttpSession login(String name,String password)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",name).param("password",password)).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
    private org.springframework.test.web.servlet.ResultActions write(MockHttpServletRequestBuilder request,Object data)throws Exception{return mvc.perform(request.session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)));}
    private JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString());}
    private JsonNode entity(String path,Map<String,Object> input)throws Exception{return body(write(post("/api/v2/catalog/"+path),input).andExpect(status().isCreated()).andReturn());}
    private void mapping(String legacy,String dimension,JsonNode entity)throws Exception{write(put("/api/v2/legacy-migration/mappings/"+legacy),Map.of("targets",List.of(Map.of("dimension",dimension,"id",entity.path("id").asText())))).andExpect(status().isOk());}
    private String oldTag(String name,String parent){jdbc.update("INSERT INTO tag(parent_id,name,normalized_name) VALUES(?,?,?)",parent==null?null:Long.parseLong(parent),name,name.toLowerCase(Locale.ROOT));return jdbc.queryForObject("SELECT id FROM tag WHERE normalized_name=? AND parent_id <=> ?",String.class,name.toLowerCase(Locale.ROOT),parent==null?null:Long.parseLong(parent));}
    private String oldAsset(String title,String tag){jdbc.update("INSERT INTO asset(title,kind) VALUES(?,'OTHER')",title);String id=jdbc.queryForObject("SELECT id FROM asset WHERE title=?",String.class,title);jdbc.update("INSERT INTO asset_tag(asset_id,tag_id) VALUES(?,?)",Long.parseLong(id),Long.parseLong(tag));return id;}
}
