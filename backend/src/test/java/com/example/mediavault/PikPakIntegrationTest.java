package com.example.mediavault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@SpringBootTest(properties={"spring.datasource.url=${DB_TEST_URL}","spring.datasource.username=${DB_TEST_USERNAME}",
    "spring.datasource.password=${DB_TEST_PASSWORD}","vault.bootstrap.username=test-admin","vault.bootstrap.password=Test-admin-2026!",
    "pikpak.enabled=true","pikpak.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".*media_vault_test(?:\\?.*)?$")
class PikPakIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void verifyDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("media_vault_test");
    }
    @Test void anonymousCsrfCookieCanCreateReadEditAndDeleteWithoutLogin() throws Exception {
        var csrfResponse=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)csrfResponse.getRequest().getSession(false);
        JsonNode token=json.readTree(csrfResponse.getResponse().getContentAsString());
        String shareCode=UUID.randomUUID().toString().replace("-", "");
        var response=mvc.perform(post("/api/pikpak/shares").session(session)
            .header(token.path("headerName").asText(),token.path("token").asText()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("url","https://mypikpak.com/s/"+shareCode,"name","integration sample","note","","passcode","test-secret"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(true)).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).path("share").path("id").asText();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("test-secret");
        assertThat(jdbc.queryForObject("SELECT passcode_cipher FROM pikpak_share WHERE id=?",String.class,id)).doesNotContain("test-secret");
        mvc.perform(get("/api/pikpak/shares/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("integration sample"));
        mvc.perform(get("/api/pikpak/shares/export.txt").param("id",id))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=\"pikpak-links.txt\""))
            .andExpect(content().string("https://mypikpak.com/s/"+shareCode+"\r\n"));
        String export=mvc.perform(get("/api/pikpak/shares/export.txt")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(export).contains("https://mypikpak.com/s/"+shareCode).doesNotContain("test-secret");
        mvc.perform(put("/api/pikpak/shares/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"edited\",\"note\":\"\",\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete("/api/pikpak/shares/"+id).param("version","0").with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/pikpak/shares/"+id).param("version","1").with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/pikpak/shares/"+id)).andExpect(status().isNotFound());
    }
    @Test void publicSharingDoesNotOpenCatalogOrBypassCsrf() throws Exception {
        mvc.perform(get("/api/pikpak/duplicate-scan")).andExpect(status().isOk());
        mvc.perform(post("/api/pikpak/duplicate-scan")).andExpect(status().isForbidden());
        mvc.perform(post("/api/pikpak/duplicate-scan/test/cancel").with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/pikpak/shares")).andExpect(status().isOk());
        mvc.perform(post("/api/pikpak/shares").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/pikpak/playback").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/assets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }
    @Test void missingSharePlaybackReturnsNotFoundWithoutContactingUpstream() throws Exception {
        mvc.perform(post("/api/pikpak/playback").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("shareId",UUID.randomUUID().toString(),"fileId","test-file"))))
            .andExpect(status().isNotFound());
    }
}
