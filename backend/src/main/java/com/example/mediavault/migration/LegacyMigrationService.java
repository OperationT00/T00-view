package com.example.mediavault.migration;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.GuardService;
import com.example.mediavault.facet.Dimension;
import com.example.mediavault.facet.FacetDto;
import com.example.mediavault.facet.FacetService;
import com.example.mediavault.tag.TagDto;
import com.example.mediavault.tag.TagService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Explicit legacy mapping; no name heuristics, no external file access, no old data deletion. */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class LegacyMigrationService {
    private final JdbcTemplate jdbc;
    private final GuardService guards;
    private final TagService tags;
    private final FacetService facets;
    public LegacyMigrationService(JdbcTemplate jdbc, GuardService guards, TagService tags, FacetService facets) {
        this.jdbc=jdbc; this.guards=guards; this.tags=tags; this.facets=facets;
    }
    public Overview overview() { return new Overview(tags.tree(), mappings()); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Overview map(String tagId, MappingInput input) {
        long id=id(tagId); guards.lockFacets(); guards.lockTags(); tags.find(tagId);
        if(input==null || input.targets()==null || input.targets().size()>100) throw ApiException.badRequest("请提供最多 100 个映射目标");
        Set<Target> checked=new LinkedHashSet<>();
        for(Target target:input.targets()) {
            if(target==null) throw ApiException.badRequest("映射目标不能为空");
            Dimension dimension=Dimension.parse(target.dimension()); id(target.id());
            facets.validateNewSelection(dimension,target.id()); checked.add(new Target(dimension.name(),target.id()));
        }
        jdbc.update("DELETE FROM legacy_facet_mapping WHERE legacy_tag_id=?",id);
        for(Target t:checked) jdbc.update("INSERT INTO legacy_facet_mapping(legacy_tag_id,dimension,entity_id) VALUES(?,?,?)",id,t.dimension(),id(t.id()));
        return overview();
    }

    public Plan plan() {
        List<TagDto> flat=tags.flat(); Map<String,TagDto> old=new LinkedHashMap<>(); flat.forEach(t->old.put(t.id(),t));
        Map<String,List<Target>> mappings=new LinkedHashMap<>(); mappings().forEach(m->mappings.put(m.legacyTagId(),m.targets()));
        Map<Dimension,Map<String,FacetDto>> vocabulary=new EnumMap<>(Dimension.class);
        facets.snapshot().forEach((d,list)->{Map<String,FacetDto> index=new LinkedHashMap<>();list.forEach(t->index.put(t.id(),t));vocabulary.put(d,index);});
        Map<String,List<String>> bound=new LinkedHashMap<>();
        jdbc.query("SELECT asset_id,tag_id FROM asset_tag ORDER BY asset_id,tag_id",rs->{bound.computeIfAbsent(rs.getString("asset_id"),k->new ArrayList<>()).add(rs.getString("tag_id"));});
        Map<Dimension,Map<String,Set<String>>> existing=new EnumMap<>(Dimension.class);
        for(Dimension d:Dimension.values()) {
            Map<String,Set<String>> links=new HashMap<>();
            jdbc.query("SELECT asset_id,entity_id FROM "+d.linkTable()+" ORDER BY asset_id,entity_id",rs->{links.computeIfAbsent(rs.getString("asset_id"),k->new HashSet<>()).add(rs.getString("entity_id"));});
            existing.put(d,links);
        }
        List<AssetPlan> assets=new ArrayList<>();
        jdbc.query("SELECT id,title,version FROM asset ORDER BY id",rs->{
            String assetId=rs.getString("id"); if(!bound.containsKey(assetId)) return;
            Set<String> relevant=new LinkedHashSet<>();
            for(String direct:bound.get(assetId)) {
                String current=direct; Set<String> visited=new HashSet<>();
                while(current!=null) { if(!visited.add(current) || !old.containsKey(current)) throw ApiException.conflict("旧标签结构异常，请先核对数据"); relevant.add(current);current=old.get(current).parentId(); }
            }
            Map<Dimension,List<FacetDto>> additions=new EnumMap<>(Dimension.class);
            for(Dimension d:Dimension.values()) additions.put(d,new ArrayList<>());
            List<TagDto> unmapped=new ArrayList<>(); Set<Target> selected=new LinkedHashSet<>();
            for(String legacy:relevant) { List<Target> targets=mappings.get(legacy);if(targets==null || targets.isEmpty()) unmapped.add(old.get(legacy)); else selected.addAll(targets); }
            for(Target target:selected) {
                Dimension d=Dimension.parse(target.dimension()); FacetDto dto=vocabulary.get(d).get(target.id());
                if(dto==null) throw ApiException.conflict("映射目标已不存在，请重新设置映射");
                if(!existing.get(d).getOrDefault(assetId,Set.of()).contains(target.id())) {
                    if(!dto.selectable()) throw ApiException.badRequest("映射目标或其归属已停用："+dto.context());
                    additions.get(d).add(dto);
                }
            }
            assets.add(new AssetPlan(assetId,rs.getString("title"),rs.getLong("version"),additions,List.copyOf(unmapped)));
        });
        return new Plan(List.copyOf(assets),digest());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Applied apply(String expectedDigest) {
        guards.lockFacets();guards.lockTags();
        // Location updates also increment versions; hold each asset row through snapshot comparison and apply.
        jdbc.queryForList("SELECT id FROM asset ORDER BY id FOR UPDATE",Long.class);
        Plan current=plan();
        if(!MessageDigest.isEqual(current.digest().getBytes(StandardCharsets.UTF_8),expectedDigest.getBytes(StandardCharsets.UTF_8))) throw ApiException.conflict("收藏、旧标签或映射已变化，请重新预览迁移");
        int updated=0;
        for(AssetPlan asset:current.assets()) {
            boolean changed=false;
            for(Dimension d:Dimension.values()) for(FacetDto target:asset.additions().get(d)) {
                facets.validateNewSelection(d,target.id());
                jdbc.update("INSERT INTO "+d.linkTable()+"(asset_id,entity_id) VALUES(?,?)",id(asset.id()),id(target.id())); changed=true;
            }
            if(changed) {jdbc.update("UPDATE asset SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",id(asset.id()));updated++;}
        }
        return new Applied(updated);
    }

    private List<Mapping> mappings() {
        Map<String,List<Target>> result=new LinkedHashMap<>();
        jdbc.query("SELECT legacy_tag_id,dimension,entity_id FROM legacy_facet_mapping ORDER BY legacy_tag_id,dimension,entity_id",rs->{result.computeIfAbsent(rs.getString("legacy_tag_id"),k->new ArrayList<>()).add(new Target(rs.getString("dimension"),rs.getString("entity_id")));});
        return result.entrySet().stream().map(e->new Mapping(e.getKey(),List.copyOf(e.getValue()))).toList();
    }
    private String digest() {
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            for(String table:List.of("asset","tag","asset_tag","legacy_facet_mapping","content_type","work_scope","catalog_character","skin","clothing_tag","creator","asset_content_type","asset_work_scope","asset_character","asset_skin","asset_clothing","asset_creator")) {
                append(hash,table);
                // Deterministic ordering of the complete low-frequency migration snapshot, independent of JDBC row order.
                List<String> rows=jdbc.query("SELECT * FROM "+table,(rs,n)->{StringBuilder row=new StringBuilder();for(int i=1;i<=rs.getMetaData().getColumnCount();i++){Object value=rs.getObject(i);String s=value==null?"<NULL>":value.toString();row.append(s.length()).append(':').append(s);}return row.toString();});
                Collections.sort(rows);rows.forEach(row->append(hash,row));
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private static void append(MessageDigest hash,String value){byte[] data=value.getBytes(StandardCharsets.UTF_8);hash.update((data.length+":").getBytes(StandardCharsets.UTF_8));hash.update(data);}
    private static long id(String value){try{if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();return Long.parseLong(value);}catch(NumberFormatException e){throw ApiException.badRequest("ID 必须为正整数");}}
    public record Target(String dimension,String id) {}
    public record Mapping(String legacyTagId,List<Target> targets) {}
    public record MappingInput(List<Target> targets) {}
    public record Overview(List<TagDto> tags,List<Mapping> mappings) {}
    public record AssetPlan(String id,String title,long version,Map<Dimension,List<FacetDto>> additions,List<TagDto> unmappedTags) {}
    public record Plan(List<AssetPlan> assets,String digest) {}
    public record Applied(int updatedAssets) {}
}
