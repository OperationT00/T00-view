package com.example.mediavault.tag;

import com.example.mediavault.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagHierarchyTest {
    @Test
    void preservesPathsAndIndependentBranchesWhenChildrenArriveBeforeParents() {
        var hierarchy = new TagHierarchy(List.of(
                node(2, 1L, "公孙离"), node(4, 3L, "古风"),
                node(1, null, "Cos"), node(3, null, "风格")));

        assertEquals("Cos / 公孙离", hierarchy.dto(2, false).path());
        assertEquals("风格 / 古风", hierarchy.dto(4, false).path());
        assertEquals("1", hierarchy.dto(2, false).parentId());
        assertEquals(2, hierarchy.tree().size());
        assertEquals("公孙离", hierarchy.tree().getFirst().children().getFirst().name());
        assertTrue(hierarchy.flat().stream().allMatch(tag -> tag.children().isEmpty()));
        assertTrue(hierarchy.isDescendantOrSelf(2, 1));
        assertFalse(hierarchy.isDescendantOrSelf(4, 1));
    }

    @Test
    void distinguishesSameNamedTagsByTheirParentPaths() {
        var hierarchy = new TagHierarchy(List.of(
                node(1, null, "Cos"), node(2, 1L, "公孙离"),
                node(3, null, "游戏角色"), node(4, 3L, "公孙离")));

        assertEquals("Cos / 公孙离", hierarchy.dto(2, false).path());
        assertEquals("游戏角色 / 公孙离", hierarchy.dto(4, false).path());
        assertEquals(4, hierarchy.flat().size());
    }

    @Test
    void acceptsEightLevelsAndRejectsNineRegardlessOfRowOrder() {
        var eightLevels = chain(8);
        Collections.reverse(eightLevels);
        var hierarchy = new TagHierarchy(eightLevels);

        assertEquals(8, hierarchy.depth(8));
        assertEquals(8, hierarchy.height(1));
        assertEquals(1, hierarchy.height(8));
        ApiException failure = assertThrows(ApiException.class, () -> new TagHierarchy(chain(9)));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.status());
        assertEquals("TAG_TREE_INVALID", failure.code());
    }

    @Test
    void rejectsSelfCyclesAndDisconnectedCyclesInsteadOfHidingThem() {
        assertThrows(ApiException.class,
                () -> new TagHierarchy(List.of(node(1, 1L, "自身"))));
        assertThrows(ApiException.class,
                () -> new TagHierarchy(List.of(node(1, null, "正常根"),
                        node(2, 3L, "A"), node(3, 2L, "B"))));
    }

    @Test
    void rejectsOrphanNodesInsteadOfReturningAnIncompleteTree() {
        ApiException failure = assertThrows(ApiException.class,
                () -> new TagHierarchy(List.of(node(1, 99L, "孤立节点"))));
        assertEquals("TAG_TREE_INVALID", failure.code());
    }

    @Test
    void preservesNonLeafNodesAndTheirVersionsInFlatAndTreeViews() {
        var hierarchy = new TagHierarchy(List.of(
                new TagHierarchy.Node(1, null, "Cos", 7), node(2, 1L, "公孙离")));

        assertEquals(7, hierarchy.dto(1, false).version());
        assertTrue(hierarchy.dto(1, false).children().isEmpty());
        assertEquals(1, hierarchy.dto(1, true).children().size());
        assertTrue(hierarchy.hasChildren(1));
        assertFalse(hierarchy.hasChildren(2));
    }

    private static TagHierarchy.Node node(long id, Long parentId, String name) {
        return new TagHierarchy.Node(id, parentId, name, 0);
    }

    private static List<TagHierarchy.Node> chain(int depth) {
        List<TagHierarchy.Node> result = new ArrayList<>();
        for (int level = 1; level <= depth; level++) {
            result.add(node(level, level == 1 ? null : (long) level - 1, "第" + level + "层"));
        }
        return result;
    }
}
