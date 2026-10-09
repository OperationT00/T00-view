package com.example.mediavault.facet;

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

@RestController
@RequestMapping("/api/v2/catalog")
public class FacetController {
    private final FacetService facets;
    public FacetController(FacetService facets) { this.facets = facets; }

    @GetMapping("/search")
    public FacetPage search(@RequestParam(value="dimension", required=false) String dimension,
                             @RequestParam(value="q", required=false) String q,
                             @RequestParam(value="workScopeId", required=false) String workScopeId,
                             @RequestParam(value="characterId", required=false) String characterId,
                             @RequestParam(value="purpose", defaultValue="FILTER") String purpose,
                             @RequestParam(value="page", defaultValue="1") int page,
                             @RequestParam(value="size", defaultValue="20") int size) {
        return facets.page(dimension == null ? null : Dimension.parse(dimension), q, workScopeId, characterId, purpose, page, size);
    }

    @GetMapping("/{path}")
    public FacetPage list(@PathVariable("path") String path,
                           @RequestParam(value="q", required=false) String q,
                           @RequestParam(value="workScopeId", required=false) String workScopeId,
                           @RequestParam(value="characterId", required=false) String characterId,
                           @RequestParam(value="purpose", defaultValue="FILTER") String purpose,
                           @RequestParam(value="page", defaultValue="1") int page,
                           @RequestParam(value="size", defaultValue="20") int size) {
        return facets.page(Dimension.fromPath(path), q, workScopeId, characterId, purpose, page, size);
    }

    @GetMapping("/{path}/{id}")
    public FacetDto find(@PathVariable("path") String path, @PathVariable("id") String id) {
        return facets.find(Dimension.fromPath(path), id);
    }

    @PostMapping("/{path}")
    public ResponseEntity<FacetDto> create(@PathVariable("path") String path, @RequestBody FacetInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facets.create(Dimension.fromPath(path), input));
    }

    @PutMapping("/{path}/{id}")
    public FacetDto update(@PathVariable("path") String path, @PathVariable("id") String id, @RequestBody FacetInput input) {
        return facets.update(Dimension.fromPath(path), id, input);
    }

    @DeleteMapping("/{path}/{id}")
    public ResponseEntity<Void> delete(@PathVariable("path") String path, @PathVariable("id") String id,
                                        @RequestParam("version") long version) {
        facets.delete(Dimension.fromPath(path), id, version);
        return ResponseEntity.noContent().build();
    }
}
