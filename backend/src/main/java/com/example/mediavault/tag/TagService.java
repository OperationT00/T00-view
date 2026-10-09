package com.example.mediavault.tag;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.GuardService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class TagService {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int SUBTREE_QUERY_TIMEOUT_SECONDS = 5;
    // ECMAScript trim: ASCII whitespace, Unicode separators and BOM; not Java's extra control characters.
    private static final Pattern QUERY_EDGE_WHITESPACE =
            Pattern.compile("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$");

    private static final RowMapper<TagHierarchy.Node> TAG_ROW = (rs, rowNum) -> {
        Number parent = (Number) rs.getObject("parent_id");
        return new TagHierarchy.Node(rs.getLong("id"), parent == null ? null : parent.longValue(),
                rs.getString("name"), rs.getLong("version"));
    };

    private static final String SUBTREE_SQL = """
            WITH RECURSIVE descendants AS (
                SELECT id, parent_id, 0 AS depth FROM tag WHERE id = ?
                UNION ALL
                SELECT child.id, child.parent_id, parent.depth + 1
                FROM tag child JOIN descendants parent ON child.parent_id = parent.id
                WHERE parent.depth < 8
            )
            SELECT id, depth FROM descendants ORDER BY depth, id
            """;

    private final JdbcTemplate jdbc;
    private final GuardService guards;

    public TagService(JdbcTemplate jdbc, GuardService guards) {
        this.jdbc = jdbc;
        this.guards = guards;
    }

    public List<TagDto> tree() {
        return loadHierarchy().tree();
    }

    public List<TagDto> flat() {
        return loadHierarchy().flat();
    }

    public TagPage page(String q, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw ApiException.badRequest("页码必须大于零，每页数量须为 1 至 100");
        }
        String query = q == null ? "" : normalize(QUERY_EDGE_WHITESPACE.matcher(q).replaceAll(""));
        List<TagDto> filtered = flat().stream()
                .filter(tag -> query.isEmpty() || normalize(tag.path()).contains(query))
                .toList();
        long offset = (long) (page - 1) * size;
        List<TagDto> items = offset >= filtered.size() ? List.of()
                : filtered.subList((int) offset, (int) Math.min(offset + size, filtered.size()));
        return new TagPage(items, filtered.size(), page, size);
    }

    public TagDto find(String id) {
        return loadHierarchy().dto(parseId(id), false);
    }

    /** Includes the selected tag itself; unknown filter tags are a client error. */
    public List<Long> subtreeIds(String id) {
        long tagId = parseId(id);
        List<Descendant> descendants = jdbc.query(connection -> {
            var statement = connection.prepareStatement(SUBTREE_SQL);
            statement.setLong(1, tagId);
            statement.setQueryTimeout(SUBTREE_QUERY_TIMEOUT_SECONDS);
            return statement;
        }, (rs, rowNum) -> new Descendant(rs.getLong("id"), rs.getInt("depth")));
        if (descendants.isEmpty()) {
            throw ApiException.badRequest("筛选标签不存在");
        }
        Set<Long> seen = new HashSet<>();
        for (Descendant descendant : descendants) {
            // The extra level is deliberately read to detect invalid/truncated structures.
            if (descendant.depth() >= TagHierarchy.MAX_DEPTH || !seen.add(descendant.id())) {
                throw TagHierarchy.invalidTree();
            }
        }
        return descendants.stream().map(Descendant::id).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TagDto create(String name, String parentId) {
        TagName validName = validateName(name);
        Long parent = parseParentId(parentId);
        guards.lockTags();
        TagHierarchy hierarchy = loadHierarchy();
        validateParent(hierarchy, parent);
        if (parent != null && hierarchy.depth(parent) + 1 > TagHierarchy.MAX_DEPTH) {
            throw ApiException.badRequest("标签最多支持 8 层");
        }
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO tag (parent_id, name, normalized_name, version, created_at, updated_at)
                        VALUES (?, ?, ?, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setObject(1, parent);
                statement.setString(2, validName.name());
                statement.setString(3, validName.normalizedName());
                return statement;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("同一父标签下已存在同名标签");
        }
        Number id = keys.getKey();
        if (id == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TAG_CREATE_FAILED", "标签创建失败");
        }
        return loadHierarchy().dto(id.longValue(), false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TagDto update(String id, String name, String parentId, long version) {
        long tagId = parseId(id);
        requireVersion(version);
        TagName validName = validateName(name);
        Long parent = parseParentId(parentId);
        guards.lockTags();
        TagHierarchy hierarchy = loadHierarchy();
        TagHierarchy.Node tag = requireTag(hierarchy, tagId);
        checkVersion(tag.version(), version);
        validateParent(hierarchy, parent);
        if (parent != null && hierarchy.isDescendantOrSelf(parent, tagId)) {
            throw ApiException.badRequest("不能将标签移到自身或其后代下");
        }
        int parentDepth = parent == null ? 0 : hierarchy.depth(parent);
        if (parentDepth + hierarchy.height(tagId) > TagHierarchy.MAX_DEPTH) {
            throw ApiException.badRequest("移动后的标签子树不能超过 8 层");
        }
        try {
            int changed = jdbc.update("""
                    UPDATE tag SET parent_id = ?, name = ?, normalized_name = ?,
                        version = version + 1, updated_at = UTC_TIMESTAMP(6)
                    WHERE id = ? AND version = ?
                    """, parent, validName.name(), validName.normalizedName(), tagId, version);
            if (changed != 1) {
                throw ApiException.conflict("标签已被修改，请刷新后重试");
            }
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("同一父标签下已存在同名标签");
        }
        return loadHierarchy().dto(tagId, false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(String id, long version) {
        long tagId = parseId(id);
        requireVersion(version);
        guards.lockTags();
        TagHierarchy hierarchy = loadHierarchy();
        TagHierarchy.Node tag = requireTag(hierarchy, tagId);
        checkVersion(tag.version(), version);
        if (hierarchy.hasChildren(tagId)) {
            throw ApiException.conflict("标签仍有子标签，请先移动或删除子标签");
        }

        List<Long> affectedAssets = jdbc.queryForList(
                "SELECT asset_id FROM asset_tag WHERE tag_id = ? ORDER BY asset_id", Long.class, tagId);
        for (long assetId : affectedAssets) {
            jdbc.queryForObject("SELECT id FROM asset WHERE id = ? FOR UPDATE", Long.class, assetId);
        }
        jdbc.update("DELETE FROM asset_tag WHERE tag_id = ?", tagId);
        for (long assetId : affectedAssets) {
            jdbc.update("UPDATE asset SET version = version + 1, updated_at = UTC_TIMESTAMP(6) WHERE id = ?", assetId);
        }
        int changed = jdbc.update("DELETE FROM tag WHERE id = ? AND version = ?", tagId, version);
        if (changed != 1) {
            throw ApiException.conflict("标签已被修改，请刷新后重试");
        }
    }

    private TagHierarchy loadHierarchy() {
        return new TagHierarchy(jdbc.query("SELECT id, parent_id, name, version FROM tag ORDER BY id", TAG_ROW));
    }

    private static TagHierarchy.Node requireTag(TagHierarchy hierarchy, long id) {
        if (!hierarchy.contains(id)) {
            throw ApiException.notFound("标签不存在");
        }
        return hierarchy.node(id);
    }

    private static void validateParent(TagHierarchy hierarchy, Long parent) {
        if (parent != null && !hierarchy.contains(parent)) {
            throw ApiException.badRequest("父标签不存在");
        }
    }

    private static TagName validateName(String rawName) {
        if (rawName == null) {
            throw ApiException.badRequest("标签名称不能为空");
        }
        String name = Normalizer.normalize(rawName, Normalizer.Form.NFC).strip();
        String normalized = normalize(name);
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || normalized.codePointCount(0, normalized.length()) > MAX_NAME_LENGTH) {
            throw ApiException.badRequest("标签名称须为 1 至 64 个字符");
        }
        return new TagName(name, normalized);
    }

    private static String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static Long parseParentId(String parentId) {
        return parentId == null ? null : parseId(parentId);
    }

    private static long parseId(String id) {
        if (id == null || !id.matches("[1-9][0-9]*")) {
            throw ApiException.badRequest("标签 ID 必须为正整数");
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("标签 ID 超出范围");
        }
    }

    private static void requireVersion(long version) {
        if (version < 0) {
            throw ApiException.badRequest("版本号不能为负数");
        }
    }

    private static void checkVersion(long current, long expected) {
        if (current != expected) {
            throw ApiException.conflict("标签已被修改，请刷新后重试");
        }
    }

    private record TagName(String name, String normalizedName) {}
    private record Descendant(long id, int depth) {}
}
