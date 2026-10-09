package com.example.mediavault.catalog;

import java.util.List;
import com.example.mediavault.common.LegacyWritePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class CatalogController {
    private final CatalogService catalog;
    private final LegacyWritePolicy legacy;
    public CatalogController(CatalogService catalog, LegacyWritePolicy legacy) { this.catalog = catalog; this.legacy = legacy; }

    @GetMapping
    public CatalogService.AssetPage list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String kind, @RequestParam(required = false) List<String> tagIds,
            @RequestParam(required = false) String sourceId, @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) Boolean untagged, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.list(q, kind, tagIds, sourceId, favorite, untagged, page, size);
    }

    @GetMapping("/{id}") public AssetDto detail(@PathVariable String id) { return catalog.detail(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AssetDto create(@RequestBody CatalogService.CreateAsset input) { legacy.requireEnabled(); return catalog.create(input); }
    @PutMapping("/{id}")
    public AssetDto update(@PathVariable String id, @RequestBody CatalogService.UpdateAsset input) { legacy.requireEnabled(); return catalog.update(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestParam Long version) { legacy.requireEnabled(); catalog.delete(id, version); }
    @PostMapping("/{id}/locations")
    public AssetDto addLocation(@PathVariable String id, @RequestBody CatalogService.LocationChange input) { legacy.requireEnabled(); return catalog.addLocation(id, input); }
    @PutMapping("/{id}/locations/{locationId}")
    public AssetDto updateLocation(@PathVariable String id, @PathVariable String locationId,
                                  @RequestBody CatalogService.LocationChange input) { legacy.requireEnabled(); return catalog.updateLocation(id, locationId, input); }
    @DeleteMapping("/{id}/locations/{locationId}")
    public AssetDto deleteLocation(@PathVariable String id, @PathVariable String locationId, @RequestParam Long version) {
        legacy.requireEnabled();
        return catalog.deleteLocation(id, locationId, version);
    }
}
