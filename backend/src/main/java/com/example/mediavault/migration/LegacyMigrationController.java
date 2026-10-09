package com.example.mediavault.migration;

import com.example.mediavault.common.ApiException;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/legacy-migration")
public class LegacyMigrationController {
    private static final String KEY=LegacyMigrationController.class.getName()+".preview";
    private final LegacyMigrationService migration;
    public LegacyMigrationController(LegacyMigrationService migration){this.migration=migration;}
    @GetMapping public LegacyMigrationService.Overview overview(){return migration.overview();}
    @PutMapping("/mappings/{tagId}") public LegacyMigrationService.Overview mapping(@PathVariable String tagId,@RequestBody LegacyMigrationService.MappingInput input){return migration.map(tagId,input);}
    @PostMapping("/preview") public Preview preview(HttpSession session){
        LegacyMigrationService.Plan plan=migration.plan();String token=UUID.randomUUID().toString();
        session.setAttribute(KEY,new StoredPreview(token,plan.digest()));return new Preview(token,plan.assets(),plan.assets().size());
    }
    @PostMapping("/apply") public LegacyMigrationService.Applied apply(@RequestBody Apply input,HttpSession session){
        Object saved=session.getAttribute(KEY);
        if(input==null||input.token()==null||!(saved instanceof StoredPreview preview)||!preview.token().equals(input.token()))throw ApiException.conflict("请先在当前会话预览迁移");
        LegacyMigrationService.Applied result=migration.apply(preview.digest());session.removeAttribute(KEY);return result;
    }
    private record StoredPreview(String token,String digest) implements java.io.Serializable {}
    public record Preview(String token,List<LegacyMigrationService.AssetPlan> assets,int total) {}
    public record Apply(String token) {}
}
