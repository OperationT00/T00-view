package com.example.mediavault.catalog;

import java.util.List;
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
@RequestMapping("/api/sources")
public class SourceController {
    private final SourceService sources;
    public SourceController(SourceService sources) { this.sources = sources; }

    @GetMapping public List<SourceDto> list() { return sources.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SourceDto create(@RequestBody SourceService.CreateSource input) { return sources.create(input); }
    @PutMapping("/{id}")
    public SourceDto update(@PathVariable String id, @RequestBody SourceService.UpdateSource input) { return sources.update(id, input); }
    @PutMapping("/{id}/reported-status")
    public SourceDto reportStatus(@PathVariable String id, @RequestBody SourceService.ReportStatus input) { return sources.reportStatus(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestParam Long version) { sources.delete(id, version); }
}
