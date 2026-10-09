package com.example.mediavault.catalog;

import com.example.mediavault.common.ApiException;
import java.util.Set;

final class CatalogInput {
    private CatalogInput() {}

    static long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("ID 必须为正整数");
        }
    }

    static long version(Long value) {
        if (value == null || value < 0) throw ApiException.badRequest("请提供有效的 version");
        return value;
    }

    static String required(String value, int max, String label) {
        String result = value == null ? "" : value.strip();
        if (result.isEmpty() || result.codePointCount(0, result.length()) > max || result.indexOf('\0') >= 0) {
            throw ApiException.badRequest(label + "不能为空且不能超过 " + max + " 个字符");
        }
        return result;
    }

    static String optional(String value, int max, String label) {
        if (value == null) return null;
        String result = value.strip();
        if (result.codePointCount(0, result.length()) > max || result.indexOf('\0') >= 0) {
            throw ApiException.badRequest(label + "不能超过 " + max + " 个字符");
        }
        return result.isEmpty() ? null : result;
    }

    static String choice(String value, Set<String> allowed, String label) {
        if (value == null || !allowed.contains(value)) throw ApiException.badRequest(label + "不合法");
        return value;
    }
}
