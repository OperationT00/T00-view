package com.example.mediavault.facet;

import java.util.List;

public record FacetPage(List<FacetDto> items, long total, int page, int size) {
    public FacetPage { items = List.copyOf(items); }
}
