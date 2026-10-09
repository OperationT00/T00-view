package com.example.mediavault.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mediavault.TestDatabase;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=${DB_TEST_URL}", "spring.datasource.username=${DB_TEST_USERNAME}",
        "spring.datasource.password=${DB_TEST_PASSWORD}",
        "vault.bootstrap.username=test-admin", "vault.bootstrap.password=Test-admin-2026!"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL", matches=".*media_vault_test(?:\\?.*)?$")
class FacetIntegrationTest {
    private static final String PASSWORD = "Test-admin-2026!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired FacetService facets;
    private MockHttpSession admin;

    @BeforeEach
    void resetOnlyIsolatedTestDatabase() throws Exception {
        TestDatabase.resetCatalog(jdbc);
        jdbc.update("DELETE FROM app_user WHERE normalized_username<>'test-admin'");
        jdbc.update("UPDATE app_user SET role='ADMIN',enabled=TRUE,password_hash=?,version=0,security_version=security_version+1 WHERE normalized_username='test-admin'",
                encoder.encode(PASSWORD));
        admin = login("test-admin");
    }

    @Test
    void sixIndependentDimensionsPreserveSkinOwnerAndUnknownOriginalWork() throws Exception {
        create("types", Map.of("name", "cos"));
        JsonNode work = create("work-scopes", Map.of("name", "示例原作"));
        JsonNode character = create("characters", Map.of("name", "示例角色", "workScopeId", id(work)));
        JsonNode skin = create("skins", Map.of("name", "示例造型", "characterId", id(character)));
        JsonNode clothing = create("clothing", Map.of("name", "旗袍"));
        JsonNode creator = create("creators", Map.of("name", "示例作者", "disambiguation", "平台 A"));
        JsonNode original = create("characters", Map.of("name", "原创人物", "disambiguation", "作者原创"));

        assertThat(skin.path("context").asText()).isEqualTo("示例原作 / 示例角色 / 示例造型");
        assertThat(skin.path("workScopeId").asText()).isEqualTo(id(work));
        assertThat(skin.path("characterId").asText()).isEqualTo(id(character));
        assertThat(original.path("workScopeId").isNull()).isTrue();
        assertThat(original.path("selectable").asBoolean()).isTrue();
        assertThat(clothing.path("workScopeId").isNull()).isTrue();
        assertThat(clothing.path("characterId").isNull()).isTrue();
        assertThat(creator.path("accounts").size()).isZero();
        assertThat(facets.snapshot()).containsKeys(Dimension.values());
        mvc.perform(get("/api/v2/catalog/skins/" + id(skin)).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.context").value("示例原作 / 示例角色 / 示例造型"));
    }

    @Test
    void identityUniquenessSupportsDifferentScopesAndDisambiguationIncludingNullWork() throws Exception {
        JsonNode a = create("work-scopes", Map.of("name", "原作 A"));
        JsonNode b = create("work-scopes", Map.of("name", "原作 B"));
        create("characters", Map.of("name", "同名", "workScopeId", id(a)));
        create("characters", Map.of("name", "同名", "workScopeId", id(b)));
        create("characters", Map.of("name", "同名", "workScopeId", id(a), "disambiguation", "另一人物"));
        create("characters", Map.of("name", "未知原作"));
        request(post("/api/v2/catalog/characters"), admin, Map.of("name", " 未知原作 "))
                .andExpect(status().isConflict());
        create("characters", Map.of("name", "未知原作", "disambiguation", "第二人"));
        create("creators", Map.of("name", "Same", "disambiguation", "作者 A"));
        create("creators", Map.of("name", "Same", "disambiguation", "作者 B"));
        request(post("/api/v2/catalog/creators"), admin, Map.of("name", " SAME ", "disambiguation", "作者 A"))
                .andExpect(status().isConflict());
        create("types", Map.of("name", "Café"));
        request(post("/api/v2/catalog/types"), admin, Map.of("name", "CAFE\u0301"))
                .andExpect(status().isConflict());
    }

    @Test
    void creatorAccountsAreMetadataWithHttpsAndNoEmbeddedCredentials() throws Exception {
        JsonNode creator = create("creators", Map.of("name", "作者", "accounts", List.of(
                Map.of("platform", "示例平台", "account", "User-A", "homepage", "https://example.com/user-a"))));
        assertThat(creator.path("accounts").get(0).path("homepage").asText()).isEqualTo("https://example.com/user-a");
        mvc.perform(get("/api/v2/catalog/search").session(admin).param("q", "user-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        for (String homepage : List.of("http://example.com/user", "javascript:alert(1)", "https://user:pass@example.com/")) {
            request(post("/api/v2/catalog/creators"), admin, Map.of("name", "错误作者", "accounts", List.of(
                    Map.of("platform", "平台", "account", "账号", "homepage", homepage))))
                    .andExpect(status().isBadRequest());
        }
        request(post("/api/v2/catalog/creators"), admin, Map.of("name", "重复账号", "accounts", List.of(
                Map.of("platform", "平台", "account", "账号"), Map.of("platform", "平台", "account", "账号"))))
                .andExpect(status().isBadRequest());
        update("creators", creator, Map.of("name", "作者", "accounts", List.of()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_account", Long.class)).isZero();
    }

    @Test
    void disabledOwnerChainBlocksNewCandidatesButLeavesHistoricalItemsReadable() throws Exception {
        JsonNode work = create("work-scopes", Map.of("name", "原作"));
        JsonNode character = create("characters", Map.of("name", "角色", "workScopeId", id(work)));
        JsonNode skin = create("skins", Map.of("name", "皮肤", "characterId", id(character)));
        update("work-scopes", work, Map.of("name", "原作", "enabled", false));

        mvc.perform(get("/api/v2/catalog/skins/" + id(skin)).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.selectable").value(false));
        mvc.perform(get("/api/v2/catalog/skins").session(admin).param("purpose", "ASSOCIATE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v2/catalog/skins").session(admin).param("purpose", "FILTER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        request(post("/api/v2/catalog/skins"), admin, Map.of("name", "新皮肤", "characterId", id(character)))
                .andExpect(status().isBadRequest());
        request(post("/api/v2/catalog/characters"), admin, Map.of("name", "新角色", "workScopeId", id(work)))
                .andExpect(status().isBadRequest());
        JsonNode renamed = update("skins", skin, Map.of("name", "历史皮肤改名", "characterId", id(character)));
        assertThat(renamed.path("selectable").asBoolean()).isFalse();
        JsonNode renamedCharacter = update("characters", character, Map.of("name", "历史角色改名", "workScopeId", id(work)));
        assertThat(renamedCharacter.path("selectable").asBoolean()).isFalse();
    }

    @Test
    void existingCollectionReferencesProtectOwnersAndDeletionWithoutTouchingAssets() throws Exception {
        JsonNode a = create("work-scopes", Map.of("name", "原作 A"));
        JsonNode b = create("work-scopes", Map.of("name", "原作 B"));
        JsonNode character = create("characters", Map.of("name", "角色", "workScopeId", id(a)));
        JsonNode other = create("characters", Map.of("name", "另一角色", "workScopeId", id(b)));
        JsonNode skin = create("skins", Map.of("name", "造型", "characterId", id(character)));
        long asset = fixtureAsset();
        jdbc.update("INSERT INTO asset_skin(asset_id,entity_id) VALUES (?,?)", asset, id(skin));

        request(put("/api/v2/catalog/characters/" + id(character)), admin,
                Map.of("name", "角色", "workScopeId", id(b), "version", 0)).andExpect(status().isConflict());
        request(put("/api/v2/catalog/skins/" + id(skin)), admin,
                Map.of("name", "造型", "characterId", id(other), "version", 0)).andExpect(status().isConflict());
        for (Map.Entry<String, JsonNode> entry : Map.of("work-scopes", a, "characters", character, "skins", skin).entrySet()) {
            mvc.perform(delete("/api/v2/catalog/" + entry.getKey() + "/" + id(entry.getValue()))
                    .session(admin).with(csrf()).param("version", "0")).andExpect(status().isConflict());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset WHERE id=?", Long.class, asset)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM asset WHERE id=?", Long.class, asset)).isZero();
        jdbc.update("DELETE FROM asset_skin WHERE asset_id=?", asset);
        JsonNode movedSkin = update("skins", skin, Map.of("name", "造型", "characterId", id(other)));
        assertThat(movedSkin.path("characterId").asText()).isEqualTo(id(other));
        JsonNode movedCharacter = update("characters", character, Map.of("name", "角色", "workScopeId", id(b)));
        assertThat(movedCharacter.path("workScopeId").asText()).isEqualTo(id(b));
    }

    @Test
    void legacyMappingsProtectTargetsAndIndirectOwnersUntilMappingIsRemoved() throws Exception {
        JsonNode work = create("work-scopes", Map.of("name", "原作"));
        JsonNode otherWork = create("work-scopes", Map.of("name", "另一个原作"));
        JsonNode character = create("characters", Map.of("name", "角色", "workScopeId", id(work)));
        JsonNode skin = create("skins", Map.of("name", "皮肤", "characterId", id(character)));
        jdbc.update("INSERT INTO tag(name,normalized_name) VALUES ('旧标签','旧标签')");
        long legacyId = jdbc.queryForObject("SELECT MAX(id) FROM tag", Long.class);
        jdbc.update("INSERT INTO legacy_facet_mapping(legacy_tag_id,dimension,entity_id) VALUES (?,'SKIN',?)", legacyId, id(skin));
        mvc.perform(delete("/api/v2/catalog/skins/" + id(skin)).session(admin).with(csrf()).param("version", "0"))
                .andExpect(status().isConflict());
        request(put("/api/v2/catalog/characters/" + id(character)), admin,
                Map.of("name", "角色", "workScopeId", id(otherWork), "version", 0)).andExpect(status().isConflict());
        jdbc.update("DELETE FROM legacy_facet_mapping WHERE legacy_tag_id=?", legacyId);
        mvc.perform(delete("/api/v2/catalog/skins/" + id(skin)).session(admin).with(csrf()).param("version", "0"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tag WHERE id=?", Long.class, legacyId)).isEqualTo(1);
    }

    @Test
    void candidateSearchIncludesContextAndUnicodeTrimWithLiteralWildcards() throws Exception {
        JsonNode work = create("work-scopes", Map.of("name", "Café"));
        JsonNode character = create("characters", Map.of("name", "角色", "workScopeId", id(work), "disambiguation", "版本 A"));
        create("skins", Map.of("name", "100%_造型", "characterId", id(character)));
        create("clothing", Map.of("name", "普通服装"));

        mvc.perform(get("/api/v2/catalog/search").session(admin).param("q", "\u00A0\uFEFF CAFE\u0301 \u2029"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/v2/catalog/search").session(admin).param("q", "%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v2/catalog/search").session(admin).param("q", "\u00A0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/v2/catalog/skins").session(admin).param("q", "版本 a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v2/catalog/search").session(admin).param("q", "不存在"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v2/catalog/skins").session(admin).param("workScopeId", id(work)).param("characterId", id(character)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v2/catalog/clothing").session(admin).param("workScopeId", id(work)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void candidatesBeyondOneHundredRemainAccessibleWithAccuratePageTotals() throws Exception {
        for (int i = 0; i < 105; i++) {
            String name = "Type-" + String.format("%03d", i);
            jdbc.update("INSERT INTO content_type(name,normalized_name) VALUES (?,?)", name, name.toLowerCase(java.util.Locale.ROOT));
        }
        mvc.perform(get("/api/v2/catalog/types").session(admin).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(105))
                .andExpect(jsonPath("$.items.length()").value(100));
        mvc.perform(get("/api/v2/catalog/types").session(admin).param("page", "2").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(105))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].name").value("Type-100"));
        mvc.perform(get("/api/v2/catalog/search").session(admin).param("dimension", "TYPE").param("q", "Type-104"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v2/catalog/types").session(admin).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v2/catalog/types").session(admin).param("page", "3").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(105))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void inappropriateFieldsMissingOwnersAndStaleVersionsAreRejected() throws Exception {
        JsonNode type = create("types", Map.of("name", "cos"));
        for (Map<String, Object> fields : List.<Map<String, Object>>of(
                Map.of("name", "错误", "disambiguation", "不适用"),
                Map.of("name", "错误", "workScopeId", "1"),
                Map.of("name", "错误", "characterId", "1"),
                Map.of("name", "错误", "accounts", List.of(Map.of("platform", "平台", "account", "账号"))))) {
            request(post("/api/v2/catalog/types"), admin, fields).andExpect(status().isBadRequest());
        }
        request(post("/api/v2/catalog/skins"), admin, Map.of("name", "无角色"))
                .andExpect(status().isBadRequest());
        request(post("/api/v2/catalog/characters"), admin, Map.of("name", "未知原作", "workScopeId", "999999999"))
                .andExpect(status().isBadRequest());
        request(put("/api/v2/catalog/types/" + id(type)), admin, Map.of("name", "cos"))
                .andExpect(status().isBadRequest());
        update("types", type, Map.of("name", "Cos"));
        request(put("/api/v2/catalog/types/" + id(type)), admin, Map.of("name", "旧编辑", "version", 0))
                .andExpect(status().isConflict());
    }

    @Test
    void viewersCanReadButCannotChangeCatalogEvenWithValidCsrf() throws Exception {
        request(post("/api/admin/users"), admin, Map.of("username", "facet-viewer", "password", PASSWORD, "role", "VIEWER"))
                .andExpect(status().isCreated());
        MockHttpSession viewer = login("facet-viewer");
        JsonNode type = create("types", Map.of("name", "cos"));
        mvc.perform(get("/api/v2/catalog/types").session(viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/v2/catalog/types/" + id(type)).session(viewer)).andExpect(status().isOk());
        request(post("/api/v2/catalog/types"), viewer, Map.of("name", "禁止"))
                .andExpect(status().isForbidden());
        request(put("/api/v2/catalog/types/" + id(type)), viewer, Map.of("name", "禁止", "version", 0))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v2/catalog/types/" + id(type)).session(viewer).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/catalog/types")).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentSameNameCharactersWithoutWorkHaveOneWinner() throws Exception {
        MockHttpSession other = login("test-admin");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> {
                start.await();
                return request(post("/api/v2/catalog/characters"), admin, Map.of("name", "原创角色"))
                        .andReturn().getResponse().getStatus();
            });
            var second = pool.submit(() -> {
                start.await();
                return request(post("/api/v2/catalog/characters"), other, Map.of("name", " 原创角色 "))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_character", Long.class)).isEqualTo(1);
    }

    @Test
    void parsingPreviewIsReadOnlyAndRequiresAdminAndCsrf() throws Exception {
        create("types", Map.of("name", "zw"));
        var input = Map.of("items", List.of(Map.of("key", "1", "fileName", "博主甲-黑丝-zw.mp4", "classificationFolder", "王者荣耀-公孙离-离恨烟")));
        var parsed = body(request(post("/api/v2/parsing/preview"), admin, input).andExpect(status().isOk()).andReturn());
        assertThat(parsed.path("ruleVersion").asText()).isEqualTo("name-v1");
        assertThat(parsed.path("items").get(0).path("candidates").toString()).contains("MATCHED", "PROPOSED", "TYPE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skin", Long.class)).isZero();
        mvc.perform(post("/api/v2/parsing/preview").session(admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        request(post("/api/admin/users"), admin, Map.of("username", "parser-viewer", "password", PASSWORD, "role", "VIEWER")).andExpect(status().isCreated());
        request(post("/api/v2/parsing/preview"), login("parser-viewer"), input).andExpect(status().isForbidden());
        request(post("/api/v2/parsing/preview"), admin, Map.of("items", List.of())).andExpect(status().isBadRequest());
        request(post("/api/v2/parsing/preview"), admin, Map.of("items", List.of(Map.of("fileName", "path/to/file.mp4")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v2/parsing/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private JsonNode create(String path, Map<String, ?> input) throws Exception {
        return body(request(post("/api/v2/catalog/" + path), admin, input).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode update(String path, JsonNode current, Map<String, ?> changes) throws Exception {
        Map<String, Object> input = new HashMap<>(changes);
        input.put("version", current.path("version").asLong());
        return body(request(put("/api/v2/catalog/" + path + "/" + id(current)), admin, input)
                .andExpect(status().isOk()).andReturn());
    }

    private ResultActions request(MockHttpServletRequestBuilder builder, MockHttpSession session, Object input) throws Exception {
        return mvc.perform(builder.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String id(JsonNode item) { return item.path("id").asText(); }

    private long fixtureAsset() {
        jdbc.update("INSERT INTO asset(title,kind) VALUES ('隔离测试收藏','IMAGE_SET')");
        return jdbc.queryForObject("SELECT MAX(id) FROM asset", Long.class);
    }
}
