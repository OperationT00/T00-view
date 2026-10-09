package com.example.mediavault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

@SpringBootTest(properties={"spring.datasource.url=${DB_TEST_URL}","spring.datasource.username=${DB_TEST_USERNAME}",
    "spring.datasource.password=${DB_TEST_PASSWORD}","vault.bootstrap.username=test-admin","vault.bootstrap.password=Test-admin-2026!",
    "vault.legacy-writes-enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".*media_vault_test(?:\\?.*)?$")
class V2AssetIntegrationTest {
    private static final String PASSWORD="Test-admin-2026!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    MockHttpSession admin;

    @BeforeEach void resetIsolatedSchema() throws Exception {
        TestDatabase.resetCatalog(jdbc);
        jdbc.update("DELETE FROM app_user WHERE normalized_username<>'test-admin'");
        jdbc.update("UPDATE app_user SET role='ADMIN',enabled=TRUE,password_hash=?,version=0,security_version=security_version+1 WHERE normalized_username='test-admin'",encoder.encode(PASSWORD));
        admin=login("test-admin",PASSWORD);
    }

    @Test void skinDerivesOnlyCharacterAndWorkAndPreservesAllSixKeys() throws Exception {
        JsonNode work=facet("work-scopes","王者荣耀",Map.of());
        JsonNode character=facet("characters","公孙离",Map.of("workScopeId",id(work)));
        JsonNode skin=facet("skins","七零雪竹",Map.of("characterId",id(character)));
        JsonNode clothing=facet("clothing","高跟鞋",Map.of());
        JsonNode asset=create("示例",Map.of("SKIN",List.of(id(skin))));
        assertThat(asset.path("directFacets").size()).isEqualTo(6);
        assertThat(asset.path("effectiveFacets").size()).isEqualTo(6);
        assertThat(asset.path("directFacets").path("CHARACTER").size()).isZero();
        assertThat(asset.path("effectiveFacets").path("CHARACTER").get(0).path("id").asText()).isEqualTo(id(character));
        assertThat(asset.path("effectiveFacets").path("WORK").get(0).path("derivedFrom").get(0).path("id").asText()).isEqualTo(id(skin));
        assertThat(asset.path("effectiveFacets").path("WORK").get(0).path("derivedFrom").get(0).path("dimension").asText()).isEqualTo("SKIN");
        assertThat(asset.path("effectiveFacets").path("CLOTHING").size()).isZero();
        list("characterIds",id(character)).andExpect(jsonPath("$.total").value(1));
        list("workIds",id(work)).andExpect(jsonPath("$.total").value(1));
        list("clothingIds",id(clothing)).andExpect(jsonPath("$.total").value(0));
    }

    @Test void allAnyAndCrossDimensionFiltersDoNotDuplicateAssets() throws Exception {
        JsonNode type=facet("types","cos",Map.of());
        JsonNode work=facet("work-scopes","游戏",Map.of());
        JsonNode a=facet("characters","A",Map.of("workScopeId",id(work))),b=facet("characters","B",Map.of("workScopeId",id(work)));
        JsonNode skin=facet("skins","造型",Map.of("characterId",id(a)));
        create("Both",Map.of("TYPE",List.of(id(type)),"CHARACTER",List.of(id(a),id(b)),"SKIN",List.of(id(skin))));
        create("One",Map.of("CHARACTER",List.of(id(a))));
        list("characterIds",id(a)+","+id(b)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v2/assets").session(admin).param("characterIds",id(a),id(b),id(a)).param("characterMode","ANY").param("workIds",id(work)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/v2/assets").session(admin).param("characterIds",id(a)+","+id(b)).param("characterMode","ANY").param("typeIds",id(type)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        JsonNode asset=body(list("typeIds",id(type)).andReturn()).path("items").get(0);
        assertThat(asset.path("effectiveFacets").path("CHARACTER").size()).isEqualTo(2);
        assertThat(asset.path("effectiveFacets").path("WORK").size()).isEqualTo(1);
        assertThat(asset.path("effectiveFacets").path("WORK").get(0).path("derivedFrom").size()).isEqualTo(3);
    }

    @Test void allSixDirectCollectionsRoundTripAndClearTogether() throws Exception {
        JsonNode type=facet("types","cos",Map.of()),work=facet("work-scopes","原作",Map.of());
        JsonNode character=facet("characters","角色",Map.of("workScopeId",id(work)));
        JsonNode skin=facet("skins","造型",Map.of("characterId",id(character)));
        JsonNode clothing=facet("clothing","旗袍",Map.of()),creator=facet("creators","示例作者",Map.of());
        Map<String,List<String>> selected=Map.of("TYPE",List.of(id(type)),"WORK",List.of(id(work)),"CHARACTER",List.of(id(character)),
            "SKIN",List.of(id(skin)),"CLOTHING",List.of(id(clothing)),"CREATOR",List.of(id(creator)));
        JsonNode asset=create("All six",selected);
        for(var entry:selected.entrySet()) assertThat(asset.path("directFacets").path(entry.getKey()).get(0).path("id").asText()).isEqualTo(entry.getValue().getFirst());
        mvc.perform(get("/api/v2/assets").session(admin).param("typeIds",id(type)).param("workIds",id(work)).param("characterIds",id(character))
            .param("skinIds",id(skin)).param("clothingIds",id(clothing)).param("creatorIds",id(creator)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        JsonNode cleared=body(request(put("/api/v2/assets/"+id(asset)),admin,updateBody("Cleared",0,Map.of())).andExpect(status().isOk()).andReturn());
        for(String dimension:selected.keySet()) {
            assertThat(cleared.path("directFacets").path(dimension).size()).isZero();
            assertThat(cleared.path("effectiveFacets").path(dimension).size()).isZero();
        }
        assertThat(cleared.path("version").asLong()).isEqualTo(1);
        list("untagged","true").andExpect(jsonPath("$.total").value(1));
    }

    @Test void missingUsesEffectiveAndUntaggedIgnoresLegacyLabels() throws Exception {
        JsonNode a=facet("characters","原创",Map.of());JsonNode skin=facet("skins","原创造型",Map.of("characterId",id(a)));
        create("Skin",Map.of("SKIN",List.of(id(skin))));
        JsonNode empty=create("Legacy",Map.of());
        jdbc.update("INSERT INTO tag(name,normalized_name) VALUES ('旧标签','旧标签')");
        long oldTag=jdbc.queryForObject("SELECT id FROM tag WHERE name='旧标签'",Long.class);
        jdbc.update("INSERT INTO asset_tag(asset_id,tag_id) VALUES (?,?)",id(empty),oldTag);
        list("missingDimensions","CHARACTER").andExpect(jsonPath("$.total").value(1));
        list("missingDimensions","WORK").andExpect(jsonPath("$.total").value(2));
        list("untagged","true").andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v2/assets").session(admin).param("missingDimensions","CHARACTER").param("characterIds",id(a)))
            .andExpect(status().isBadRequest());
    }

    @Test void stoppedParentRejectsNewSkinButOldDirectSelectionCanBeRetained() throws Exception {
        JsonNode work=facet("work-scopes","原作",Map.of()),character=facet("characters","角色",Map.of("workScopeId",id(work)));
        JsonNode skin=facet("skins","皮肤",Map.of("characterId",id(character)));
        JsonNode old=create("Existing",Map.of("SKIN",List.of(id(skin))));
        request(put("/api/v2/catalog/work-scopes/"+id(work)),admin,Map.of("name","原作","enabled",false,"version",0)).andExpect(status().isOk());
        request(post("/api/v2/assets"),admin,Map.of("title","New","kind","IMAGE_SET","facetIds",Map.of("SKIN",List.of(id(skin)))))
            .andExpect(status().isBadRequest());
        request(put("/api/v2/assets/"+id(old)),admin,updateBody("Kept",0,Map.of("SKIN",List.of(id(skin)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.effectiveFacets.WORK[0].enabled").value(false));
        list("workIds",id(work)).andExpect(jsonPath("$.total").value(1));
    }

    @Test void updateRequiresAllDimensionsAndRollsBackInvalidSelection() throws Exception {
        JsonNode type=facet("types","cos",Map.of());JsonNode asset=create("Original",Map.of("TYPE",List.of(id(type))));
        request(put("/api/v2/assets/"+id(asset)),admin,Map.of("title","Partial","kind","VIDEO","version",0,"facetIds",Map.of("TYPE",List.of(id(type)))))
            .andExpect(status().isBadRequest());
        request(put("/api/v2/assets/"+id(asset)),admin,updateBody("Invalid",0,Map.of("TYPE",List.of("999999999")))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v2/assets/"+id(asset)).session(admin)).andExpect(jsonPath("$.title").value("Original")).andExpect(jsonPath("$.version").value(0));
        request(put("/api/v2/assets/"+id(asset)),admin,updateBody("Changed",0,Map.of())).andExpect(status().isOk());
        request(put("/api/v2/assets/"+id(asset)),admin,updateBody("Stale",0,Map.of())).andExpect(status().isConflict());
    }

    @Test void editingSixFacetsKeepsLegacyTagsAndCannotWriteThemThroughV2() throws Exception {
        JsonNode asset=create("Original",Map.of());
        jdbc.update("INSERT INTO tag(name,normalized_name) VALUES ('待归类','待归类')");
        long tag=jdbc.queryForObject("SELECT id FROM tag WHERE name='待归类'",Long.class);
        jdbc.update("INSERT INTO asset_tag(asset_id,tag_id) VALUES (?,?)",id(asset),tag);
        request(put("/api/v2/assets/"+id(asset)),admin,updateBody("Changed",0,Map.of())).andExpect(status().isOk()).andExpect(jsonPath("$.tags[0].name").value("待归类"));
        var attack=updateBody("Attack",1,Map.of());attack.put("tagIds",List.of());
        request(put("/api/v2/assets/"+id(asset)),admin,attack).andExpect(status().isBadRequest());
        list("tagIds",Long.toString(tag)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_tag WHERE asset_id=?",Long.class,id(asset))).isEqualTo(1);
    }

    @Test void locationsReturnV2AndDeleteCascadesOnlyMetadata() throws Exception {
        JsonNode source=body(request(post("/api/sources"),admin,Map.of("name","Disk","type","LOCAL_DISK")).andExpect(status().isCreated()).andReturn());
        JsonNode type=facet("types","cos",Map.of()),asset=create("Sample",Map.of("TYPE",List.of(id(type))));
        JsonNode located=body(request(post("/api/v2/assets/"+id(asset)+"/locations"),admin,Map.of("sourceId",id(source),"locatorType","LOCAL_PATH","locator","D:/fake/sample.mp4","version",0))
            .andExpect(status().isOk()).andExpect(jsonPath("$.directFacets.TYPE[0].id").value(id(type))).andExpect(jsonPath("$.version").value(1)).andReturn());
        String location=located.path("locations").get(0).path("id").asText();
        request(put("/api/v2/assets/"+id(asset)+"/locations/"+location),admin,Map.of("sourceId",id(source),"locatorType","LOCAL_PATH","locator","D:/fake/moved.mp4","version",1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(delete("/api/v2/assets/"+id(asset)).session(admin).with(csrf()).param("version","2")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_content_type",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_location",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storage_source",Long.class)).isEqualTo(1);
    }

    @Test void viewerCannotWriteAndQueryRejectsUnknownModesOrIds() throws Exception {
        request(post("/api/admin/users"),admin,Map.of("username","viewer-v2","password",PASSWORD)).andExpect(status().isCreated());
        MockHttpSession viewer=login("viewer-v2",PASSWORD);
        mvc.perform(get("/api/v2/assets").session(viewer)).andExpect(status().isOk());
        request(post("/api/v2/assets"),viewer,Map.of("title","Forbidden","kind","VIDEO")).andExpect(status().isForbidden());
        list("typeMode","SOME").andExpect(status().isBadRequest());
        list("typeIds","999999999").andExpect(status().isBadRequest());
        list("favorite","yes").andExpect(status().isBadRequest());
        list("page","0").andExpect(status().isBadRequest());
        list("page","999999").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    private JsonNode facet(String path,String name,Map<String,Object> extra) throws Exception {
        var body=new HashMap<String,Object>(extra);body.put("name",name);
        return body(request(post("/api/v2/catalog/"+path),admin,body).andExpect(status().isCreated()).andReturn());
    }
    private JsonNode create(String title,Map<String,List<String>> selected) throws Exception {
        return body(request(post("/api/v2/assets"),admin,Map.of("title",title,"kind","IMAGE_SET","facetIds",selected)).andExpect(status().isCreated()).andReturn());
    }
    private HashMap<String,Object> updateBody(String title,long version,Map<String,List<String>> selected) {
        var all=new HashMap<String,List<String>>();for(String dimension:List.of("TYPE","WORK","CHARACTER","SKIN","CLOTHING","CREATOR")) all.put(dimension,selected.getOrDefault(dimension,List.of()));
        var body=new HashMap<String,Object>();body.put("title",title);body.put("kind","IMAGE_SET");body.put("version",version);body.put("facetIds",all);return body;
    }
    private org.springframework.test.web.servlet.ResultActions list(String key,String value) throws Exception {
        return mvc.perform(get("/api/v2/assets").session(admin).param(key,value));
    }
    private MockHttpSession login(String username,String password) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",username).param("password",password))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private org.springframework.test.web.servlet.ResultActions request(MockHttpServletRequestBuilder builder,MockHttpSession session,Object body) throws Exception {
        return mvc.perform(builder.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private JsonNode body(MvcResult result) throws Exception {return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
    private static String id(JsonNode value) {return value.path("id").asText();}
}
