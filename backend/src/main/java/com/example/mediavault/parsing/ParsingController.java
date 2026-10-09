package com.example.mediavault.parsing;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.facet.FacetService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/parsing")
public class ParsingController {
    private final FacetService facets;
    private final ResourceNameParser parser = new ResourceNameParser();
    public ParsingController(FacetService facets) { this.facets = facets; }
    public record Request(List<ResourceNameParser.Input> items) {}
    public record Response(String ruleVersion, List<ResourceNameParser.Result> items) {}
    @PostMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public Response preview(@RequestBody Request request) {
        if (request == null || request.items() == null || request.items().isEmpty() || request.items().size() > 100
                || request.items().stream().anyMatch(java.util.Objects::isNull))
            throw ApiException.badRequest("请提交 1 至 100 条文件信息");
        var vocabulary = facets.snapshot().values().stream().flatMap(List::stream).map(f ->
                new ResourceNameParser.Entry(f.id(), ResourceNameParser.Dimension.valueOf(f.dimension().name()),
                        f.name(), f.context(), f.workScopeId(), f.characterId(), f.selectable(), f.version())).toList();
        try { return new Response(ResourceNameParser.VERSION, request.items().stream().map(i -> parser.parse(i, vocabulary)).toList()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest(e.getMessage()); }
    }
}
