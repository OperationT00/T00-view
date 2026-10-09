package com.example.mediavault.catalog;

import com.example.mediavault.common.ApiException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SourceService {
    private final JdbcTemplate jdbc;
    private static final Set<String> TYPES = Set.of("LOCAL_DISK", "PIKPAK", "BAIDU");
    private static final Set<String> STATUSES = Set.of("UNKNOWN", "ONLINE", "OFFLINE");

    public SourceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<SourceDto> list() {
        return jdbc.query("SELECT * FROM storage_source ORDER BY id", SourceService::map);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SourceDto create(CreateSource input) {
        if (input == null) throw ApiException.badRequest("缺少来源内容");
        String name = CatalogInput.required(input.name(), 128, "来源名称");
        String type = CatalogInput.choice(input.type(), TYPES, "来源类型");
        String note = CatalogInput.optional(input.note(), 8000, "备注");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO storage_source(name,type,note) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name); statement.setString(2, type); statement.setString(3, note);
            return statement;
        }, key);
        return read(key.getKey().longValue(), false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SourceDto update(String sourceId, UpdateSource input) {
        if (input == null) throw ApiException.badRequest("缺少来源内容");
        long id = CatalogInput.id(sourceId);
        String name = CatalogInput.required(input.name(), 128, "来源名称");
        String note = CatalogInput.optional(input.note(), 8000, "备注");
        SourceDto current = read(id, true);
        checkVersion(current, input.version());
        jdbc.update("UPDATE storage_source SET name=?,note=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?", name, note, id);
        return read(id, false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SourceDto reportStatus(String sourceId, ReportStatus input) {
        if (input == null) throw ApiException.badRequest("缺少来源状态");
        long id = CatalogInput.id(sourceId);
        String status = CatalogInput.choice(input.status(), STATUSES, "来源状态");
        SourceDto current = read(id, true);
        checkVersion(current, input.version());
        jdbc.update("UPDATE storage_source SET reported_status=?,status_reported_at=UTC_TIMESTAMP(6),version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?", status, id);
        return read(id, false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String sourceId, Long version) {
        long id = CatalogInput.id(sourceId);
        SourceDto current = read(id, true);
        checkVersion(current, version);
        Long references = jdbc.queryForObject("SELECT COUNT(*) FROM asset_location WHERE source_id=?", Long.class, id);
        if (references != null && references > 0) throw ApiException.conflict("来源仍被位置引用，请先移除或修改相关位置");
        jdbc.update("DELETE FROM storage_source WHERE id=?", id);
    }

    private SourceDto read(long id, boolean lock) {
        List<SourceDto> values = jdbc.query("SELECT * FROM storage_source WHERE id=?" + (lock ? " FOR UPDATE" : ""), SourceService::map, id);
        if (values.isEmpty()) throw ApiException.notFound("来源不存在");
        return values.getFirst();
    }

    private static void checkVersion(SourceDto current, Long expected) {
        if (current.version() != CatalogInput.version(expected)) throw ApiException.conflict("来源已变更，请刷新后重试");
    }

    static SourceDto map(ResultSet row, int index) throws SQLException {
        return new SourceDto(Long.toString(row.getLong("id")), row.getString("name"), row.getString("type"),
            row.getString("reported_status"), instant(row, "status_reported_at"), row.getString("note"), row.getLong("version"));
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        // DATETIME is a UTC wall-clock value; do not interpret it through the host timezone.
        var value = row.getObject(column, java.time.LocalDateTime.class);
        return value == null ? null : value.toInstant(java.time.ZoneOffset.UTC);
    }

    public record CreateSource(String name, String type, String note) {}
    public record UpdateSource(String name, String note, Long version) {}
    public record ReportStatus(String status, Long version) {}
}
