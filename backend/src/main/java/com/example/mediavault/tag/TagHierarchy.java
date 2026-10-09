package com.example.mediavault.tag;

import com.example.mediavault.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A validated snapshot; no inferred ancestors are written to asset_tag. */
final class TagHierarchy {
    static final int MAX_DEPTH = 8;

    record Node(long id, Long parentId, String name, long version) {}

    private static final Comparator<Node> NODE_ORDER =
            Comparator.comparing(Node::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(Node::id);

    private final Map<Long, Node> nodes = new LinkedHashMap<>();
    private final Map<Long, List<Node>> children = new HashMap<>();
    private final Map<Long, String> paths = new HashMap<>();
    private final Map<Long, Integer> depths = new HashMap<>();
    private final List<Node> roots = new ArrayList<>();

    TagHierarchy(List<Node> rows) {
        for (Node node : rows) {
            if (node.id() <= 0 || nodes.put(node.id(), node) != null) {
                throw invalidTree();
            }
        }
        for (Node node : nodes.values()) {
            if (node.parentId() == null) {
                roots.add(node);
            } else {
                if (!nodes.containsKey(node.parentId())) {
                    throw invalidTree();
                }
                children.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        roots.sort(NODE_ORDER);
        children.values().forEach(list -> list.sort(NODE_ORDER));
        for (long id : nodes.keySet()) {
            resolvePath(id, new HashSet<>());
        }
    }

    boolean contains(long id) {
        return nodes.containsKey(id);
    }

    Node node(long id) {
        return nodes.get(id);
    }

    int depth(long id) {
        return depths.get(id);
    }

    boolean hasChildren(long id) {
        return !children.getOrDefault(id, List.of()).isEmpty();
    }

    boolean isDescendantOrSelf(long candidate, long ancestor) {
        Node current = nodes.get(candidate);
        while (current != null) {
            if (current.id() == ancestor) {
                return true;
            }
            current = current.parentId() == null ? null : nodes.get(current.parentId());
        }
        return false;
    }

    int height(long id) {
        int height = 1;
        for (Node child : children.getOrDefault(id, List.of())) {
            height = Math.max(height, height(child.id()) + 1);
        }
        return height;
    }

    List<TagDto> tree() {
        return roots.stream().map(node -> dto(node.id(), true)).toList();
    }

    List<TagDto> flat() {
        return nodes.values().stream()
                .sorted(Comparator.comparing((Node node) -> paths.get(node.id()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(Node::id))
                .map(node -> dto(node.id(), false))
                .toList();
    }

    TagDto dto(long id, boolean includeChildren) {
        Node node = nodes.get(id);
        if (node == null) {
            throw ApiException.notFound("标签不存在");
        }
        List<TagDto> childDtos = includeChildren
                ? children.getOrDefault(id, List.of()).stream().map(child -> dto(child.id(), true)).toList()
                : List.of();
        return new TagDto(Long.toString(id), node.name(),
                node.parentId() == null ? null : node.parentId().toString(),
                paths.get(id), node.version(), childDtos);
    }

    private void resolvePath(long id, Set<Long> visiting) {
        if (paths.containsKey(id)) {
            return;
        }
        if (visiting.size() >= MAX_DEPTH || !visiting.add(id)) {
            throw invalidTree();
        }
        Node node = nodes.get(id);
        if (node.parentId() == null) {
            depths.put(id, 1);
            paths.put(id, node.name());
        } else {
            resolvePath(node.parentId(), visiting);
            int depth = depths.get(node.parentId()) + 1;
            if (depth > MAX_DEPTH) {
                throw invalidTree();
            }
            depths.put(id, depth);
            paths.put(id, paths.get(node.parentId()) + " / " + node.name());
        }
        visiting.remove(id);
    }

    static ApiException invalidTree() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TAG_TREE_INVALID", "标签结构异常，请联系管理员");
    }
}
