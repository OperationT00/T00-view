package com.example.mediavault.facet;

import com.example.mediavault.common.ApiException;
import java.util.Locale;

public enum Dimension {
    TYPE("content_type", "asset_content_type", "types"),
    WORK("work_scope", "asset_work_scope", "work-scopes"),
    CHARACTER("catalog_character", "asset_character", "characters"),
    SKIN("skin", "asset_skin", "skins"),
    CLOTHING("clothing_tag", "asset_clothing", "clothing"),
    CREATOR("creator", "asset_creator", "creators");

    private final String table;
    private final String linkTable;
    private final String path;

    Dimension(String table, String linkTable, String path) {
        this.table = table;
        this.linkTable = linkTable;
        this.path = path;
    }

    public String table() { return table; }
    public String linkTable() { return linkTable; }
    public String path() { return path; }

    public static Dimension parse(String value) {
        if (value == null) throw ApiException.badRequest("缺少标签维度");
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("未知标签维度");
        }
    }

    public static Dimension fromPath(String path) {
        for (Dimension dimension : values()) {
            if (dimension.path.equals(path)) return dimension;
        }
        throw ApiException.notFound("标签维度不存在");
    }
}
