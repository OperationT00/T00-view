package com.example.mediavault.facet;

import java.util.List;

public record FacetDto(String id, Dimension dimension, String name, String context,
                       String disambiguation, String note, boolean enabled, boolean selectable,
                       long version, String workScopeId, String characterId,
                       List<CreatorAccountDto> accounts) {
    public FacetDto {
        accounts = List.copyOf(accounts);
    }
}
