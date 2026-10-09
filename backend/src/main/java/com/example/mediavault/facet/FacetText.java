package com.example.mediavault.facet;

import com.example.mediavault.common.ApiException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class FacetText {
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$");

    private FacetText() {}

    static String trim(String value) {
        return EDGE_WHITESPACE.matcher(value).replaceAll("");
    }

    static String normalize(String value) {
        return Normalizer.normalize(trim(value), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    static String required(String value, int maximum, String label) {
        String cleaned = optional(value, maximum, label);
        if (cleaned == null) throw ApiException.badRequest(label + "不能为空");
        return cleaned;
    }

    static String optional(String value, int maximum, String label) {
        if (value == null) return null;
        String cleaned = Normalizer.normalize(trim(value), Normalizer.Form.NFC);
        if (cleaned.isEmpty()) return null;
        if (cleaned.codePointCount(0, cleaned.length()) > maximum) {
            throw ApiException.badRequest(label + "长度超出限制");
        }
        return cleaned;
    }

    static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw ApiException.badRequest("标签 ID 必须为正整数");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("标签 ID 超出范围");
        }
    }
}
