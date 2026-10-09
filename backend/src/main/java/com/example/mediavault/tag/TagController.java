package com.example.mediavault.tag;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.LegacyWritePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    private final TagService tags;
    private final LegacyWritePolicy legacy;

    public TagController(TagService tags, LegacyWritePolicy legacy) {
        this.tags = tags;
        this.legacy = legacy;
    }

    @GetMapping("/tree")
    public List<TagDto> tree() {
        return tags.tree();
    }

    @GetMapping
    public TagPage list(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size) {
        return tags.page(q, page, size);
    }

    @PostMapping
    public ResponseEntity<TagDto> create(@RequestBody CreateTagRequest request) {
        legacy.requireEnabled();
        return ResponseEntity.status(HttpStatus.CREATED).body(tags.create(request.name(), request.parentId()));
    }

    @PutMapping("/{id}")
    public TagDto update(@PathVariable("id") String id, @RequestBody UpdateTagRequest request) {
        legacy.requireEnabled();
        if (request.version() == null) {
            throw ApiException.badRequest("请提供标签版本号");
        }
        return tags.update(id, request.name(), request.parentId(), request.version());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id,
                                       @RequestParam("version") long version) {
        legacy.requireEnabled();
        tags.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    public record CreateTagRequest(String name, String parentId) {}
    public record UpdateTagRequest(String name, String parentId, Long version) {}
}
