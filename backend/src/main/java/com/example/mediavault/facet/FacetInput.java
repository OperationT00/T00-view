package com.example.mediavault.facet;

import java.util.List;

public record FacetInput(String name, String disambiguation, String note, Boolean enabled,
                         String workScopeId, String characterId, List<CreatorAccountDto> accounts,
                         Long version) {}
