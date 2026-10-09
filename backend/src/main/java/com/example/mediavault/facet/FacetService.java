package com.example.mediavault.facet;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.GuardService;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FacetService {
    private static final Comparator<FacetDto> ORDER = Comparator
            .comparing((FacetDto facet) -> facet.dimension().ordinal())
            .thenComparing(facet -> FacetText.normalize(facet.context()))
            .thenComparingLong(facet -> Long.parseLong(facet.id()));
    private final JdbcTemplate jdbc;
    private final GuardService guards;

    public FacetService(JdbcTemplate jdbc, GuardService guards) {
        this.jdbc = jdbc;
        this.guards = guards;
    }

    /** Full catalog snapshot, not a capped candidate page. */
    public Map<Dimension, List<FacetDto>> snapshot() {
        Map<Dimension, List<Row>> rows = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) rows.put(dimension, readRows(dimension));
        Map<Long, Row> works = index(rows.get(Dimension.WORK));
        Map<Long, Row> characters = index(rows.get(Dimension.CHARACTER));
        Map<Long, List<CreatorAccountDto>> accounts = new HashMap<>();
        jdbc.query("SELECT creator_id,platform,account,homepage FROM creator_account ORDER BY platform,account", rs -> {
            accounts.computeIfAbsent(rs.getLong("creator_id"), ignored -> new ArrayList<>())
                    .add(new CreatorAccountDto(rs.getString("platform"), rs.getString("account"), rs.getString("homepage")));
        });
        Map<Dimension, List<FacetDto>> result = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) {
            result.put(dimension, rows.get(dimension).stream()
                    .map(row -> toDto(dimension, row, works, characters, accounts)).sorted(ORDER).toList());
        }
        return Collections.unmodifiableMap(result);
    }

    public FacetDto find(Dimension dimension, String id) {
        long entityId = FacetText.id(id);
        return snapshot().get(dimension).stream().filter(facet -> facet.id().equals(Long.toString(entityId)))
                .findFirst().orElseThrow(() -> ApiException.notFound("标签条目不存在"));
    }

    /** Existing inactive associations are handled by callers; this checks only newly selected IDs. */
    public FacetDto validateNewSelection(Dimension dimension, String id) {
        FacetDto facet;
        try {
            facet = find(dimension, id);
        } catch (ApiException ex) {
            if (ex.status() == HttpStatus.NOT_FOUND) throw ApiException.badRequest("所选标签条目不存在");
            throw ex;
        }
        if (!facet.selectable()) throw ApiException.badRequest("标签或其归属条目已停用，不能新增关联");
        return facet;
    }

    public FacetPage page(Dimension dimension, String q, String workScopeId, String characterId,
                           String purpose, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw ApiException.badRequest("页码必须大于零，每页数量须为 1 至 100");
        }
        Purpose use = Purpose.parse(purpose);
        String queryText = FacetText.optional(q, 512, "搜索词");
        String query = queryText == null ? "" : FacetText.normalize(queryText);
        Long workId = workScopeId == null ? null : FacetText.id(workScopeId);
        Long ownerId = characterId == null ? null : FacetText.id(characterId);
        if (dimension != null && workId != null && dimension != Dimension.CHARACTER && dimension != Dimension.SKIN) {
            throw ApiException.badRequest("该维度不支持原作条件");
        }
        if (dimension != null && ownerId != null && dimension != Dimension.SKIN) {
            throw ApiException.badRequest("该维度不支持角色条件");
        }
        Map<Dimension, List<FacetDto>> catalog = snapshot();
        requireFilter(catalog, Dimension.WORK, workId);
        requireFilter(catalog, Dimension.CHARACTER, ownerId);
        List<FacetDto> filtered = catalog.entrySet().stream()
                .filter(entry -> dimension == null || entry.getKey() == dimension)
                .flatMap(entry -> entry.getValue().stream())
                .filter(facet -> use != Purpose.ASSOCIATE || facet.selectable())
                .filter(facet -> workId == null || Objects.equals(facet.workScopeId(), workId.toString()))
                .filter(facet -> ownerId == null || Objects.equals(facet.characterId(), ownerId.toString()))
                .filter(facet -> query.isEmpty() || FacetText.normalize(facet.name()).contains(query)
                        || FacetText.normalize(facet.context()).contains(query))
                .sorted(ORDER).toList();
        long offset = ((long) page - 1) * size;
        List<FacetDto> items = offset >= filtered.size() ? List.of()
                : filtered.subList((int) offset, (int) Math.min(offset + size, filtered.size()));
        return new FacetPage(items, filtered.size(), page, size);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FacetDto create(Dimension dimension, FacetInput input) {
        Validated validated = validateInput(dimension, input);
        guards.lockFacets();
        validateOwner(dimension, validated);
        boolean enabled = input.enabled() == null || input.enabled();
        List<String> columns = new ArrayList<>(List.of("name", "normalized_name", "note", "enabled"));
        List<Object> values = new ArrayList<>();
        addCommon(values, validated, enabled);
        addSpecific(dimension, validated, columns, values);
        String sql = "INSERT INTO " + dimension.table() + " (" + String.join(",", columns)
                + ",created_at,updated_at) VALUES (" + placeholders(values.size()) + ",UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))";
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
                return statement;
            }, key);
        } catch (DuplicateKeyException ex) {
            throw duplicate();
        }
        Number generated = key.getKey();
        if (generated == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FACET_CREATE_FAILED", "标签创建失败");
        long id = generated.longValue();
        if (dimension == Dimension.CREATOR) replaceAccounts(id, validated.accounts());
        return find(dimension, Long.toString(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FacetDto update(Dimension dimension, String id, FacetInput input) {
        long entityId = FacetText.id(id);
        Validated validated = validateInput(dimension, input);
        long version = requireVersion(input.version());
        guards.lockFacets();
        FacetDto current = find(dimension, id);
        checkVersion(current.version(), version);
        Long oldOwner = dimension == Dimension.CHARACTER ? nullableId(current.workScopeId())
                : dimension == Dimension.SKIN ? nullableId(current.characterId()) : null;
        Long newOwner = dimension == Dimension.CHARACTER ? validated.workId()
                : dimension == Dimension.SKIN ? validated.characterId() : null;
        if (!Objects.equals(oldOwner, newOwner)) {
            if (hasCollectionReference(dimension, entityId)) {
                throw ApiException.conflict("已有收藏或旧标签映射引用，不能直接修改归属");
            }
            validateOwner(dimension, validated);
        }
        boolean enabled = input.enabled() == null ? current.enabled() : input.enabled();
        List<String> columns = new ArrayList<>(List.of("name", "normalized_name", "note", "enabled"));
        List<Object> values = new ArrayList<>();
        addCommon(values, validated, enabled);
        addSpecific(dimension, validated, columns, values);
        values.add(entityId);
        values.add(version);
        String assignments = columns.stream().map(column -> column + "=?").collect(Collectors.joining(","));
        try {
            int updated = jdbc.update("UPDATE " + dimension.table() + " SET " + assignments
                    + ",version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND version=?", values.toArray());
            if (updated != 1) throw ApiException.conflict("标签条目已变更，请刷新后重试");
        } catch (DuplicateKeyException ex) {
            throw duplicate();
        }
        if (dimension == Dimension.CREATOR) replaceAccounts(entityId, validated.accounts());
        return find(dimension, id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Dimension dimension, String id, long version) {
        long entityId = FacetText.id(id);
        requireVersion(version);
        guards.lockFacets();
        checkVersion(find(dimension, id).version(), version);
        if (hasCollectionReference(dimension, entityId)
                || dimension == Dimension.WORK && exists("SELECT 1 FROM catalog_character WHERE work_scope_id=? LIMIT 1", entityId)
                || dimension == Dimension.CHARACTER && exists("SELECT 1 FROM skin WHERE character_id=? LIMIT 1", entityId)) {
            throw ApiException.conflict("标签条目仍被引用，请先移除引用或停用");
        }
        if (dimension == Dimension.CREATOR) jdbc.update("DELETE FROM creator_account WHERE creator_id=?", entityId);
        try {
            int deleted = jdbc.update("DELETE FROM " + dimension.table() + " WHERE id=? AND version=?", entityId, version);
            if (deleted != 1) throw ApiException.conflict("标签条目已变更，请刷新后重试");
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("标签条目仍被引用，请先移除引用或停用");
        }
    }

    private List<Row> readRows(Dimension dimension) {
        String extra = dimension == Dimension.CHARACTER ? "work_scope_id,NULL AS character_id,disambiguation"
                : dimension == Dimension.SKIN ? "NULL AS work_scope_id,character_id,NULL AS disambiguation"
                : dimension == Dimension.CREATOR ? "NULL AS work_scope_id,NULL AS character_id,disambiguation"
                : "NULL AS work_scope_id,NULL AS character_id,NULL AS disambiguation";
        return jdbc.query("SELECT id,name,note,enabled,version," + extra + " FROM " + dimension.table(), (rs, rowNum) -> {
            Number work = (Number) rs.getObject("work_scope_id");
            Number owner = (Number) rs.getObject("character_id");
            String disambiguation = rs.getString("disambiguation");
            return new Row(rs.getLong("id"), rs.getString("name"), rs.getString("note"), rs.getBoolean("enabled"),
                    rs.getLong("version"), work == null ? null : work.longValue(), owner == null ? null : owner.longValue(),
                    disambiguation == null || disambiguation.isEmpty() ? null : disambiguation);
        });
    }

    private static Map<Long, Row> index(List<Row> rows) {
        return rows.stream().collect(Collectors.toMap(Row::id, row -> row));
    }

    private static FacetDto toDto(Dimension dimension, Row row, Map<Long, Row> works,
                                   Map<Long, Row> characters, Map<Long, List<CreatorAccountDto>> accounts) {
        String context = describe(row);
        boolean selectable = row.enabled();
        Long workId = row.workId();
        if (dimension == Dimension.CHARACTER && workId != null) {
            Row work = requireRelated(works, workId);
            context = work.name() + " / " + context;
            selectable &= work.enabled();
        } else if (dimension == Dimension.SKIN) {
            Row character = requireRelated(characters, row.characterId());
            workId = character.workId();
            context = describe(character) + " / " + row.name();
            selectable &= character.enabled();
            if (workId != null) {
                Row work = requireRelated(works, workId);
                context = work.name() + " / " + context;
                selectable &= work.enabled();
            }
        }
        List<CreatorAccountDto> ownerAccounts = dimension == Dimension.CREATOR
                ? accounts.getOrDefault(row.id(), List.of()) : List.of();
        if (!ownerAccounts.isEmpty()) {
            context += " · " + ownerAccounts.stream().map(account -> account.platform() + "/" + account.account())
                    .collect(Collectors.joining("、"));
        }
        return new FacetDto(Long.toString(row.id()), dimension, row.name(), context, row.disambiguation(), row.note(),
                row.enabled(), selectable, row.version(), workId == null ? null : workId.toString(),
                row.characterId() == null ? null : row.characterId().toString(), ownerAccounts);
    }

    private static String describe(Row row) {
        return row.name() + (row.disambiguation() == null ? "" : " · " + row.disambiguation());
    }

    private static Row requireRelated(Map<Long, Row> rows, Long id) {
        Row related = rows.get(id);
        if (related == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FACET_RELATION_INVALID", "标签归属异常");
        return related;
    }

    private static void requireFilter(Map<Dimension, List<FacetDto>> catalog, Dimension dimension, Long id) {
        if (id != null && catalog.get(dimension).stream().noneMatch(facet -> facet.id().equals(id.toString()))) {
            throw ApiException.badRequest("筛选归属条目不存在");
        }
    }

    private void validateOwner(Dimension dimension, Validated validated) {
        if (dimension == Dimension.CHARACTER && validated.workId() != null) {
            validateNewSelection(Dimension.WORK, validated.workId().toString());
        } else if (dimension == Dimension.SKIN) {
            validateNewSelection(Dimension.CHARACTER, validated.characterId().toString());
        }
    }

    private boolean hasCollectionReference(Dimension dimension, long id) {
        if (exists("SELECT 1 FROM " + dimension.linkTable() + " WHERE entity_id=? LIMIT 1", id)
                || mapped(dimension, id)) return true;
        if (dimension == Dimension.CHARACTER) {
            return exists("SELECT 1 FROM asset_skin a JOIN skin s ON s.id=a.entity_id WHERE s.character_id=? LIMIT 1", id)
                    || exists("SELECT 1 FROM legacy_facet_mapping m JOIN skin s ON m.dimension='SKIN' AND s.id=m.entity_id WHERE s.character_id=? LIMIT 1", id);
        }
        if (dimension == Dimension.WORK) {
            return exists("SELECT 1 FROM asset_character a JOIN catalog_character c ON c.id=a.entity_id WHERE c.work_scope_id=? LIMIT 1", id)
                    || exists("SELECT 1 FROM asset_skin a JOIN skin s ON s.id=a.entity_id JOIN catalog_character c ON c.id=s.character_id WHERE c.work_scope_id=? LIMIT 1", id)
                    || exists("SELECT 1 FROM legacy_facet_mapping m JOIN catalog_character c ON m.dimension='CHARACTER' AND c.id=m.entity_id WHERE c.work_scope_id=? LIMIT 1", id)
                    || exists("SELECT 1 FROM legacy_facet_mapping m JOIN skin s ON m.dimension='SKIN' AND s.id=m.entity_id JOIN catalog_character c ON c.id=s.character_id WHERE c.work_scope_id=? LIMIT 1", id);
        }
        return false;
    }

    private boolean mapped(Dimension dimension, long id) {
        return exists("SELECT 1 FROM legacy_facet_mapping WHERE dimension=? AND entity_id=? LIMIT 1", dimension.name(), id);
    }

    private boolean exists(String sql, Object... arguments) {
        return !jdbc.queryForList(sql, arguments).isEmpty();
    }

    private static Validated validateInput(Dimension dimension, FacetInput input) {
        if (input == null) throw ApiException.badRequest("缺少标签内容");
        String name = FacetText.required(input.name(), 128, "名称");
        String normalized = FacetText.normalize(name);
        if (normalized.codePointCount(0, normalized.length()) > 128) throw ApiException.badRequest("规范化名称长度超出限制");
        String disambiguation = FacetText.optional(input.disambiguation(), 128, "辨识备注");
        if (dimension != Dimension.CHARACTER && dimension != Dimension.CREATOR && disambiguation != null) {
            throw ApiException.badRequest("该维度不支持辨识备注");
        }
        String normalizedDisambiguation = disambiguation == null ? "" : FacetText.normalize(disambiguation);
        if (normalizedDisambiguation.codePointCount(0, normalizedDisambiguation.length()) > 128) {
            throw ApiException.badRequest("规范化辨识备注长度超出限制");
        }
        String note = FacetText.optional(input.note(), 8000, "说明");
        if (dimension != Dimension.CHARACTER && input.workScopeId() != null) {
            throw ApiException.badRequest("该维度不能指定原作归属");
        }
        if (dimension != Dimension.SKIN && input.characterId() != null) {
            throw ApiException.badRequest("该维度不能指定角色归属");
        }
        Long workId = input.workScopeId() == null ? null : FacetText.id(input.workScopeId());
        Long characterId = input.characterId() == null ? null : FacetText.id(input.characterId());
        if (dimension == Dimension.SKIN && characterId == null) throw ApiException.badRequest("皮肤必须指定所属角色");
        List<CreatorAccountDto> accounts = validateAccounts(input.accounts());
        if (dimension != Dimension.CREATOR && !accounts.isEmpty()) throw ApiException.badRequest("仅创作者支持平台账号");
        return new Validated(name, normalized, disambiguation, normalizedDisambiguation, note, workId, characterId, accounts);
    }

    private static List<CreatorAccountDto> validateAccounts(List<CreatorAccountDto> raw) {
        if (raw == null) return List.of();
        if (raw.size() > 20) throw ApiException.badRequest("每位创作者最多保存 20 个平台账号");
        List<CreatorAccountDto> result = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        for (CreatorAccountDto account : raw) {
            if (account == null) throw ApiException.badRequest("平台账号不能为空");
            String platform = FacetText.required(account.platform(), 64, "平台名称");
            String identity = FacetText.required(account.account(), 128, "平台账号");
            String homepage = FacetText.optional(account.homepage(), 2048, "主页链接");
            if (platform.codePoints().anyMatch(Character::isISOControl)
                    || identity.codePoints().anyMatch(Character::isISOControl)) {
                throw ApiException.badRequest("平台或账号不能包含控制字符");
            }
            if (!seen.add(List.of(platform, identity))) throw ApiException.badRequest("平台账号重复");
            if (homepage != null) validateHomepage(homepage);
            result.add(new CreatorAccountDto(platform, identity, homepage));
        }
        return List.copyOf(result);
    }

    private static void validateHomepage(String homepage) {
        try {
            URI uri = new URI(homepage);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() > 65535 || homepage.codePoints().anyMatch(Character::isISOControl)) {
                throw ApiException.badRequest("创作者主页必须是无内嵌账号信息的 HTTPS 链接");
            }
        } catch (URISyntaxException ex) {
            throw ApiException.badRequest("创作者主页格式不合法");
        }
    }

    private static void addCommon(List<Object> values, Validated validated, boolean enabled) {
        values.add(validated.name());
        values.add(validated.normalizedName());
        values.add(validated.note());
        values.add(enabled);
    }

    private static void addSpecific(Dimension dimension, Validated validated, List<String> columns, List<Object> values) {
        if (dimension == Dimension.CHARACTER) {
            columns.add("work_scope_id");
            values.add(validated.workId());
        } else if (dimension == Dimension.SKIN) {
            columns.add("character_id");
            values.add(validated.characterId());
        }
        if (dimension == Dimension.CHARACTER || dimension == Dimension.CREATOR) {
            columns.addAll(List.of("disambiguation", "normalized_disambiguation"));
            values.add(validated.disambiguation() == null ? "" : validated.disambiguation());
            values.add(validated.normalizedDisambiguation());
        }
    }

    private void replaceAccounts(long creatorId, List<CreatorAccountDto> accounts) {
        jdbc.update("DELETE FROM creator_account WHERE creator_id=?", creatorId);
        for (CreatorAccountDto account : accounts) {
            jdbc.update("INSERT INTO creator_account(creator_id,platform,account,homepage) VALUES (?,?,?,?)",
                    creatorId, account.platform(), account.account(), account.homepage());
        }
    }

    private static long requireVersion(Long version) {
        if (version == null || version < 0) throw ApiException.badRequest("请提供非负版本号");
        return version;
    }

    private static void checkVersion(long current, long expected) {
        if (current != expected) throw ApiException.conflict("标签条目已变更，请刷新后重试");
    }

    private static Long nullableId(String value) { return value == null ? null : FacetText.id(value); }
    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private static ApiException duplicate() { return ApiException.conflict("同一作用域下已存在相同名称和辨识备注的条目"); }

    private enum Purpose {
        FILTER, ASSOCIATE, MANAGE;
        static Purpose parse(String value) {
            if (value == null) return FILTER;
            try { return valueOf(FacetText.normalize(value).toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw ApiException.badRequest("未知候选用途"); }
        }
    }

    private record Row(long id, String name, String note, boolean enabled, long version,
                         Long workId, Long characterId, String disambiguation) {}
    private record Validated(String name, String normalizedName, String disambiguation,
                               String normalizedDisambiguation, String note, Long workId,
                               Long characterId, List<CreatorAccountDto> accounts) {}
}
