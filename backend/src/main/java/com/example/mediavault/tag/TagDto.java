package com.example.mediavault.tag;

import java.util.List;

public record TagDto(
        String id,
        String name,
        String parentId,
        String path,
        long version,
        List<TagDto> children) {

    public TagDto {
        children = List.copyOf(children);
    }
}
