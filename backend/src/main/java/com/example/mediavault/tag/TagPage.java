package com.example.mediavault.tag;

import java.util.List;

public record TagPage(List<TagDto> items, long total, int page, int size) {
    public TagPage {
        items = List.copyOf(items);
    }
}
