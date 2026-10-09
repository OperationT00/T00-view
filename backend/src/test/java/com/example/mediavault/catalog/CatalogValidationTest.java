package com.example.mediavault.catalog;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.GuardService;
import com.example.mediavault.tag.TagService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogValidationTest {
    private JdbcTemplate jdbc;
    private CatalogService catalog;

    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        catalog = new CatalogService(jdbc, mock(GuardService.class), mock(TagService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "http://example.test/private", "https://alice:secret@example.test/private",
        "https://example.test/private?X-Amz-Signature=private-secret", "https://example.test/private?%73ignature=private-secret",
        "https://example.test/private?Expires=123", "https://example.test:99999/private", "https://example.test/private\nsecret"})
    void rejectsUnsafeOrKnownTemporaryLinksWithoutEchoingPrivateInput(String url) {
        when(jdbc.queryForList(contains("SELECT type"), eq(String.class), anyLong())).thenReturn(List.of("PIKPAK"));
        ApiException error = assertThrows(ApiException.class, () -> catalog.create(new CatalogService.CreateAsset(
            "示例", "VIDEO", null, false, List.of(), List.of(new CatalogService.LocationInput("1", "WEB_URL", url, null)))));
        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertFalse(error.getMessage().contains(url));
        assertFalse(error.getMessage().contains("private-secret"));
    }

    @Test void rejectsCloudWebLocationsAttachedToLocalDisks() {
        when(jdbc.queryForList(contains("SELECT type"), eq(String.class), anyLong())).thenReturn(List.of("LOCAL_DISK"));
        ApiException error = assertThrows(ApiException.class, () -> catalog.create(new CatalogService.CreateAsset(
            "示例", "VIDEO", null, false, List.of(), List.of(new CatalogService.LocationInput("1", "WEB_URL", "https://example.test/page", null)))));
        assertEquals(HttpStatus.BAD_REQUEST, error.status());
    }

    @Test void rejectsUnknownAssetKindBeforeAnyDatabaseAccess() {
        assertThrows(ApiException.class, () -> catalog.create(new CatalogService.CreateAsset("示例", "UNKNOWN", null, false, List.of(), List.of())));
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1 OR 1=1", "9223372036854775808", "", "1.0"})
    void rejectsMalformedIdsBeforeDatabaseAccess(String id) {
        assertThrows(ApiException.class, () -> catalog.detail(id));
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsOversizedPagesBeforeDatabaseAccess() {
        assertThrows(ApiException.class, () -> catalog.list(null, null, null, null, null, null, 1, 101));
        verifyNoInteractions(jdbc);
    }
}
