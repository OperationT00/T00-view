package com.example.mediavault.catalog;

import com.example.mediavault.tag.TagDto;
import java.time.Instant;
import java.util.List;

public record AssetDto(String id, String title, String kind, String description, boolean favorite,
                       long version, Instant createdAt, Instant updatedAt,
                       List<TagDto> tags, List<LocationDto> locations) {}
