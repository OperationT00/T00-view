package com.example.mediavault.catalog;

import com.example.mediavault.facet.CreatorAccountDto;
import com.example.mediavault.facet.Dimension;
import com.example.mediavault.facet.FacetDto;
import com.example.mediavault.tag.TagDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record V2AssetDto(String id, String title, String kind, String description, boolean favorite,
        long version, Instant createdAt, Instant updatedAt, List<TagDto> tags, List<LocationDto> locations,
        Map<Dimension,List<FacetDto>> directFacets, Map<Dimension,List<EffectiveFacet>> effectiveFacets) {
    public record FacetReference(Dimension dimension, String id, String name) {}
    public record EffectiveFacet(String id, Dimension dimension, String name, String context,
            String disambiguation, String note, boolean enabled, boolean selectable, long version,
            String workScopeId, String characterId, List<CreatorAccountDto> accounts,
            List<FacetReference> derivedFrom) {
        static EffectiveFacet of(FacetDto value, List<FacetReference> reasons) {
            return new EffectiveFacet(value.id(),value.dimension(),value.name(),value.context(),value.disambiguation(),
                value.note(),value.enabled(),value.selectable(),value.version(),value.workScopeId(),
                value.characterId(),value.accounts(),List.copyOf(reasons));
        }
    }
}
