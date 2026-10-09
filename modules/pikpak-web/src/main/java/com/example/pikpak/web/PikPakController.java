package com.example.pikpak.web;

import com.example.pikpak.*;
import com.example.pikpak.PikPakModels.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/pikpak")
public class PikPakController {
    private final PikPakModule module;
    private final PikPakDuplicateScanner scanner;
    public PikPakController(PikPakModule module, PikPakDuplicateScanner scanner){this.module=module;this.scanner=scanner;}
    @GetMapping("/duplicate-scan") PikPakDuplicateScanner.Result scanStatus(){return scanner.current();}
    @PostMapping("/duplicate-scan") PikPakDuplicateScanner.Result scanStart(){return scanner.start();}
    @PostMapping("/duplicate-scan/{id}/cancel") PikPakDuplicateScanner.Result scanCancel(@PathVariable String id){return scanner.cancel(id);}
    public record Edit(String name,String note,String passcode,long version){}
    public record Play(String shareId,String fileId){}
    @GetMapping("/shares") List<Share> list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="50")int limit){return module.list(q,offset,limit);}
    @GetMapping(value="/shares/export.txt", produces="text/plain;charset=UTF-8")
    org.springframework.http.ResponseEntity<String> export(@RequestParam(required=false) String id) {
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"pikpak-links.txt\"")
            .header("Cache-Control", "no-store").body(module.exportLinks(id));
    }
    @PostMapping("/shares") AddResult add(@RequestBody ImportItem input){return module.add(input.url(),input.name(),input.note(),input.passcode());}
    @PostMapping("/shares/batch") List<ImportResult> batch(@RequestBody List<ImportItem> input){return module.addBatch(input);}
    @GetMapping("/shares/{id}") Share get(@PathVariable String id){return module.get(id);}
    @PutMapping("/shares/{id}") Share edit(@PathVariable String id,@RequestBody Edit edit){return module.update(id,edit.version(),edit.name(),edit.note(),edit.passcode());}
    @DeleteMapping("/shares/{id}") void delete(@PathVariable String id,@RequestParam long version){module.delete(id,version);}
    @GetMapping("/shares/{id}/entries") Page browse(@PathVariable String id,@RequestParam(defaultValue="")String parentId,@RequestParam(defaultValue="")String cursor){return module.browse(id,parentId,cursor);}
    @PostMapping("/playback") Playback playback(@RequestBody Play play){return module.playback(play.shareId(),play.fileId());}
}
