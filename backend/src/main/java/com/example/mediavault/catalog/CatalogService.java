package com.example.mediavault.catalog;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.GuardService;
import com.example.mediavault.tag.TagDto;
import com.example.mediavault.tag.TagService;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CatalogService {
    private static final Set<String> KINDS = Set.of("VIDEO", "IMAGE_SET", "OTHER");
    private static final Set<String> LOCATOR_TYPES = Set.of("LOCAL_PATH", "CLOUD_PATH", "WEB_URL");
    private static final Set<String> TEMPORARY_QUERY_KEYS = Set.of("x-amz-signature", "x-amz-credential",
        "x-amz-expires", "x-goog-signature", "x-goog-credential", "x-goog-expires", "x-oss-signature",
        "x-oss-credential", "x-oss-expires", "x-oss-security-token", "x-amz-security-token",
        "ossaccesskeyid", "signature", "sign", "expires", "expiration", "auth_key");
    private final JdbcTemplate jdbc;
    private final GuardService guards;
    private final TagService tags;

    public CatalogService(JdbcTemplate jdbc, GuardService guards, TagService tags) {
        this.jdbc = jdbc; this.guards = guards; this.tags = tags;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public AssetPage list(String q, String kind, List<String> tagIds, String sourceId,
                          Boolean favorite, Boolean untagged, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw ApiException.badRequest("page 至少为 1，size 应在 1 到 100 之间");
        String query = CatalogInput.optional(q, 256, "搜索词");
        List<Object> arguments = new ArrayList<>();
        StringBuilder conditions = new StringBuilder(" WHERE 1=1");
        if (query != null) {
            String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            conditions.append(" AND (a.title LIKE ? ESCAPE '!' OR a.description LIKE ? ESCAPE '!')");
            arguments.add(pattern); arguments.add(pattern);
        }
        if (kind != null && !kind.isBlank()) {
            conditions.append(" AND a.kind=?");
            arguments.add(CatalogInput.choice(kind, KINDS, "收藏类型"));
        }
        for (long selectedId : ids(tagIds)) {
            List<Long> branch = tags.subtreeIds(Long.toString(selectedId));
            conditions.append(" AND EXISTS (SELECT 1 FROM asset_tag atf WHERE atf.asset_id=a.id AND atf.tag_id IN (")
                .append(placeholders(branch.size())).append("))");
            arguments.addAll(branch);
        }
        if (sourceId != null && !sourceId.isBlank()) {
            long selectedSource = CatalogInput.id(sourceId);
            if (!existsSource(selectedSource)) throw ApiException.badRequest("筛选的来源不存在");
            conditions.append(" AND EXISTS (SELECT 1 FROM asset_location alf WHERE alf.asset_id=a.id AND alf.source_id=?)");
            arguments.add(selectedSource);
        }
        if (favorite != null) { conditions.append(" AND a.favorite=?"); arguments.add(favorite); }
        if (Boolean.TRUE.equals(untagged)) conditions.append(" AND NOT EXISTS (SELECT 1 FROM asset_tag atu WHERE atu.asset_id=a.id)");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM asset a" + conditions, Long.class, arguments.toArray());
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(size); pageArguments.add(((long) page - 1) * size);
        List<AssetRow> rows = jdbc.query("SELECT a.* FROM asset a" + conditions + " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
            CatalogService::mapAsset, pageArguments.toArray());
        return new AssetPage(enrich(rows), total == null ? 0 : total, page, size);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public AssetDto detail(String assetId) { return enriched(readAsset(CatalogInput.id(assetId), false)); }

    /** Batch projection shared by V2, preserving the caller's stable asset order. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<AssetDto> details(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, AssetRow> rows = jdbc.query("SELECT * FROM asset WHERE id IN (" + placeholders(ids.size()) + ")",
            CatalogService::mapAsset, ids.toArray()).stream().collect(Collectors.toMap(AssetRow::id, row -> row));
        return enrich(ids.stream().filter(rows::containsKey).map(rows::get).toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AssetDto create(CreateAsset input) {
        if (input == null) throw ApiException.badRequest("缺少收藏内容");
        String title = CatalogInput.required(input.title(), 255, "标题");
        String kind = CatalogInput.choice(input.kind(), KINDS, "收藏类型");
        String description = CatalogInput.optional(input.description(), 8000, "说明");
        List<Long> selectedTags = ids(input.tagIds());
        List<LocationInput> locations = input.locations() == null ? List.of() : input.locations();
        if (locations.size() > 100) throw ApiException.badRequest("每个收藏最多提交 100 个位置");
        guards.lockTags();
        validateTags(selectedTags);
        // Validate and share-lock sources before inserting locations; a concurrent source deletion cannot race the FK check.
        List<ValidatedLocation> checkedLocations = locations.stream().map(this::validateLocation).toList();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO asset(title,kind,description,favorite) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title); statement.setString(2, kind); statement.setString(3, description);
            statement.setBoolean(4, Boolean.TRUE.equals(input.favorite()));
            return statement;
        }, key);
        long id = key.getKey().longValue();
        replaceTags(id, selectedTags);
        for (ValidatedLocation location : checkedLocations) insertLocation(id, location);
        return enriched(readAsset(id, false));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AssetDto update(String assetId, UpdateAsset input) {
        if (input == null) throw ApiException.badRequest("缺少收藏内容");
        long id = CatalogInput.id(assetId);
        String title = CatalogInput.required(input.title(), 255, "标题");
        String kind = CatalogInput.choice(input.kind(), KINDS, "收藏类型");
        String description = CatalogInput.optional(input.description(), 8000, "说明");
        List<Long> selectedTags = ids(input.tagIds());
        guards.lockTags();
        AssetRow current = readAsset(id, true);
        checkVersion(current, input.version());
        validateTags(selectedTags);
        jdbc.update("UPDATE asset SET title=?,kind=?,description=?,favorite=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",
            title, kind, description, Boolean.TRUE.equals(input.favorite()), id);
        replaceTags(id, selectedTags);
        return enriched(readAsset(id, false));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String assetId, Long version) {
        long id = CatalogInput.id(assetId);
        guards.lockTags();
        AssetRow current = readAsset(id, true);
        checkVersion(current, version);
        jdbc.update("DELETE FROM asset_location WHERE asset_id=?", id);
        jdbc.update("DELETE FROM asset_tag WHERE asset_id=?", id);
        jdbc.update("DELETE FROM asset WHERE id=?", id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AssetDto addLocation(String assetId, LocationChange input) {
        if (input == null) throw ApiException.badRequest("缺少位置内容");
        long id = CatalogInput.id(assetId);
        AssetRow current = readAsset(id, true);
        checkVersion(current, input.version());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM asset_location WHERE asset_id=?", Integer.class, id);
        if (count != null && count >= 100) throw ApiException.badRequest("每个收藏最多保存 100 个位置");
        insertLocation(id, validateLocation(input.location()));
        incrementAssetVersion(id);
        return enriched(readAsset(id, false));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AssetDto updateLocation(String assetId, String locationId, LocationChange input) {
        if (input == null) throw ApiException.badRequest("缺少位置内容");
        long id = CatalogInput.id(assetId);
        long location = CatalogInput.id(locationId);
        AssetRow current = readAsset(id, true);
        checkVersion(current, input.version());
        requireLocation(id, location);
        ValidatedLocation checked = validateLocation(input.location());
        jdbc.update("UPDATE asset_location SET source_id=?,locator_type=?,locator=?,note=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND asset_id=?",
            checked.sourceId(), checked.locatorType(), checked.locator(), checked.note(), location, id);
        incrementAssetVersion(id);
        return enriched(readAsset(id, false));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AssetDto deleteLocation(String assetId, String locationId, Long version) {
        long id = CatalogInput.id(assetId);
        long location = CatalogInput.id(locationId);
        AssetRow current = readAsset(id, true);
        checkVersion(current, version);
        requireLocation(id, location);
        jdbc.update("DELETE FROM asset_location WHERE id=? AND asset_id=?", location, id);
        incrementAssetVersion(id);
        return enriched(readAsset(id, false));
    }

    private void requireLocation(long assetId, long locationId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM asset_location WHERE id=? AND asset_id=?", Integer.class, locationId, assetId);
        if (count == null || count == 0) throw ApiException.notFound("位置不存在");
    }

    private void incrementAssetVersion(long id) {
        jdbc.update("UPDATE asset SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?", id);
    }

    private void validateTags(List<Long> selectedTags) {
        if (selectedTags.isEmpty()) return;
        List<Long> found = jdbc.queryForList("SELECT id FROM tag WHERE id IN (" + placeholders(selectedTags.size()) + ")",
            Long.class, selectedTags.toArray());
        if (found.size() != selectedTags.size()) throw ApiException.badRequest("所选标签不存在，请刷新标签列表");
    }

    private void replaceTags(long assetId, List<Long> selectedTags) {
        jdbc.update("DELETE FROM asset_tag WHERE asset_id=?", assetId);
        for (long tag : selectedTags) jdbc.update("INSERT INTO asset_tag(asset_id,tag_id) VALUES (?,?)", assetId, tag);
    }

    private ValidatedLocation validateLocation(LocationInput input) {
        if (input == null) throw ApiException.badRequest("位置不能为空");
        long source = CatalogInput.id(input.sourceId());
        String locatorType = CatalogInput.choice(input.locatorType(), LOCATOR_TYPES, "位置类型");
        String locator = CatalogInput.required(input.locator(), 4096, "位置");
        if (locator.codePoints().anyMatch(Character::isISOControl)) throw ApiException.badRequest("位置不能包含控制字符");
        String note = CatalogInput.optional(input.note(), 8000, "位置备注");
        List<String> sourceTypes = jdbc.queryForList("SELECT type FROM storage_source WHERE id=? FOR SHARE", String.class, source);
        if (sourceTypes.isEmpty()) throw ApiException.badRequest("位置使用的来源不存在");
        boolean disk = sourceTypes.getFirst().equals("LOCAL_DISK");
        if ((disk && !locatorType.equals("LOCAL_PATH")) || (!disk && locatorType.equals("LOCAL_PATH"))) {
            throw ApiException.badRequest("位置类型与存储来源不匹配");
        }
        if (locatorType.equals("WEB_URL")) validateUrl(locator);
        return new ValidatedLocation(source, locatorType, locator, note);
    }

    private static void validateUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535) {
                throw ApiException.badRequest("网页位置必须是无内嵌账号信息的 HTTPS 链接");
            }
            if (uri.getRawQuery() != null) {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String rawKey = parameter.split("=", 2)[0];
                    String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (TEMPORARY_QUERY_KEYS.contains(key)) throw ApiException.badRequest("请保存稳定的原始页面链接，不能使用已知临时签名链接");
                }
            }
        } catch (java.net.URISyntaxException | IllegalArgumentException ex) {
            throw ApiException.badRequest("网页位置链接格式不合法");
        }
    }

    private void insertLocation(long assetId, ValidatedLocation location) {
        jdbc.update("INSERT INTO asset_location(asset_id,source_id,locator_type,locator,note) VALUES (?,?,?,?,?)",
            assetId, location.sourceId(), location.locatorType(), location.locator(), location.note());
    }

    private boolean existsSource(long sourceId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM storage_source WHERE id=?", Integer.class, sourceId);
        return count != null && count > 0;
    }

    private AssetRow readAsset(long id, boolean lock) {
        List<AssetRow> rows = jdbc.query("SELECT * FROM asset WHERE id=?" + (lock ? " FOR UPDATE" : ""), CatalogService::mapAsset, id);
        if (rows.isEmpty()) throw ApiException.notFound("收藏不存在");
        return rows.getFirst();
    }

    private static void checkVersion(AssetRow current, Long version) {
        if (current.version() != CatalogInput.version(version)) throw ApiException.conflict("收藏已变更，请刷新后重试");
    }

    private AssetDto enriched(AssetRow row) { return enrich(List.of(row)).getFirst(); }

    private List<AssetDto> enrich(List<AssetRow> rows) {
        if (rows.isEmpty()) return List.of();
        List<Long> assetIds = rows.stream().map(AssetRow::id).toList();
        Map<String, TagDto> tagMap = tags.flat().stream().collect(Collectors.toMap(TagDto::id, tag -> tag));
        Map<Long, List<TagDto>> assetTags = new HashMap<>();
        jdbc.query("SELECT asset_id,tag_id FROM asset_tag WHERE asset_id IN (" + placeholders(assetIds.size()) + ") ORDER BY tag_id", row -> {
            TagDto tag = tagMap.get(Long.toString(row.getLong("tag_id")));
            if (tag != null) assetTags.computeIfAbsent(row.getLong("asset_id"), ignored -> new ArrayList<>()).add(tag);
        }, assetIds.toArray());
        Map<Long, List<LocationDto>> locations = new HashMap<>();
        jdbc.query("SELECT l.*,s.name AS source_name,s.type AS source_type,s.reported_status,s.status_reported_at "
            + "FROM asset_location l JOIN storage_source s ON s.id=l.source_id WHERE l.asset_id IN ("
            + placeholders(assetIds.size()) + ") ORDER BY l.id", row -> {
                LocationDto location = new LocationDto(Long.toString(row.getLong("id")), Long.toString(row.getLong("source_id")),
                    row.getString("source_name"), row.getString("source_type"), row.getString("reported_status"),
                    SourceService.instant(row, "status_reported_at"), row.getString("locator_type"), row.getString("locator"), row.getString("note"));
                locations.computeIfAbsent(row.getLong("asset_id"), ignored -> new ArrayList<>()).add(location);
            }, assetIds.toArray());
        return rows.stream().map(row -> new AssetDto(Long.toString(row.id()), row.title(), row.kind(), row.description(), row.favorite(),
            row.version(), row.createdAt(), row.updatedAt(), List.copyOf(assetTags.getOrDefault(row.id(), List.of())),
            List.copyOf(locations.getOrDefault(row.id(), List.of())))).toList();
    }

    private static AssetRow mapAsset(ResultSet row, int index) throws SQLException {
        return new AssetRow(row.getLong("id"), row.getString("title"), row.getString("kind"), row.getString("description"),
            row.getBoolean("favorite"), row.getLong("version"), SourceService.instant(row, "created_at"), SourceService.instant(row, "updated_at"));
    }

    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }

    private static List<Long> ids(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) throw ApiException.badRequest("标签 ID 不能为空");
            for (String part : value.split(",", -1)) {
                result.add(CatalogInput.id(part.strip()));
                if (result.size() > 100) throw ApiException.badRequest("最多选择 100 个标签");
            }
        }
        return List.copyOf(result);
    }

    public record AssetPage(List<AssetDto> items, long total, int page, int size) {}
    public record CreateAsset(String title, String kind, String description, Boolean favorite, List<String> tagIds, List<LocationInput> locations) {}
    public record UpdateAsset(String title, String kind, String description, Boolean favorite, List<String> tagIds, Long version) {}
    public record LocationInput(String sourceId, String locatorType, String locator, String note) {}
    public record LocationChange(String sourceId, String locatorType, String locator, String note, Long version) {
        LocationInput location() { return new LocationInput(sourceId, locatorType, locator, note); }
    }
    private record ValidatedLocation(long sourceId, String locatorType, String locator, String note) {}
    private record AssetRow(long id, String title, String kind, String description, boolean favorite, long version, Instant createdAt, Instant updatedAt) {}
}
