package com.example.mediavault.catalog;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.GuardService;
import com.example.mediavault.facet.Dimension;
import com.example.mediavault.facet.FacetDto;
import com.example.mediavault.facet.FacetService;
import com.example.mediavault.tag.TagDto;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class V2AssetService {
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    private final FacetService facets;
    private final GuardService guards;
    public V2AssetService(JdbcTemplate jdbc,CatalogService catalog,FacetService facets,GuardService guards) {
        this.jdbc=jdbc;this.catalog=catalog;this.facets=facets;this.guards=guards;
    }

    // One MySQL consistent snapshot keeps total, rows, links and entity projections mutually compatible.
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public V2Page list(AssetQuery query) {
        if(query.page()<1 || query.size()<1 || query.size()>100) throw ApiException.badRequest("页码至少为1，每页数量为1至100");
        Map<Dimension,Map<String,FacetDto>> snapshot=index(facets.snapshot());
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        List<Object> args=new ArrayList<>();
        String q=CatalogInput.optional(query.q(),256,"搜索词");
        if(q!=null) {
            String pattern="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            where.append(" AND (a.title LIKE ? ESCAPE '!' OR a.description LIKE ? ESCAPE '!')");args.add(pattern);args.add(pattern);
        }
        if(query.kind()!=null && !query.kind().isBlank()) {
            CatalogInput.choice(query.kind(),Set.of("VIDEO","IMAGE_SET","OTHER"),"文件形式");
            where.append(" AND a.kind=?");args.add(query.kind());
        }
        if(query.sourceId()!=null && !query.sourceId().isBlank()) {
            long source=CatalogInput.id(query.sourceId());
            if(jdbc.queryForObject("SELECT COUNT(*) FROM storage_source WHERE id=?",Long.class,source)==0)
                throw ApiException.badRequest("来源不存在");
            where.append(" AND EXISTS (SELECT 1 FROM asset_location loc WHERE loc.asset_id=a.id AND loc.source_id=?)");args.add(source);
        }
        if(query.favorite()!=null) {where.append(" AND a.favorite=?");args.add(query.favorite());}
        for(Dimension dimension:Dimension.values()) {
            List<Long> selected=ids(query.facetIds().get(dimension));
            String mode=query.modes().getOrDefault(dimension,"ALL");
            if(!Set.of("ALL","ANY").contains(mode)) throw ApiException.badRequest("维度模式仅允许 ALL 或 ANY");
            if(!selected.isEmpty() && query.missing().contains(dimension)) throw ApiException.badRequest("同一维度不能同时指定具体值和未标注");
            for(long id:selected) if(!snapshot.get(dimension).containsKey(Long.toString(id))) throw ApiException.badRequest("筛选的维度条目不存在");
            if(query.missing().contains(dimension)) where.append(" AND NOT (").append(effectiveExists(dimension,null)).append(")");
            if(mode.equals("ALL")) for(long id:selected) {
                where.append(" AND (").append(effectiveExists(dimension,List.of(id))).append(")");
                addEffectiveArgs(args,dimension,List.of(id));
            }
            else if(!selected.isEmpty()) {
                where.append(" AND (").append(effectiveExists(dimension,selected)).append(")");
                addEffectiveArgs(args,dimension,selected);
            }
            if(Boolean.TRUE.equals(query.untagged())) where.append(" AND NOT EXISTS (SELECT 1 FROM ").append(dimension.linkTable()).append(" un WHERE un.asset_id=a.id)");
        }
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM asset a"+where,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args);paged.add(query.size());paged.add(((long)query.page()-1)*query.size());
        List<Long> orderedIds=jdbc.queryForList("SELECT a.id FROM asset a"+where+" ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",Long.class,paged.toArray());
        return new V2Page(enrich(catalog.details(orderedIds),snapshot),count==null?0:count,query.page(),query.size());
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public V2AssetDto detail(String id) {return enrich(List.of(catalog.detail(id)),index(facets.snapshot())).getFirst();}

    @PreAuthorize("hasRole('ADMIN')")
    public V2AssetDto create(CreateRequest request) {
        if(request==null) throw ApiException.badRequest("缺少收藏内容");
        Map<Dimension,List<Long>> selected=selection(request.facetIds(),false);
        lockDirectories();
        validateSelection(selected,emptySelection());
        AssetDto asset=catalog.create(new CatalogService.CreateAsset(request.title(),request.kind(),request.description(),request.favorite(),List.of(),request.locations()));
        writeSelection(CatalogInput.id(asset.id()),selected);
        return detail(asset.id());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public V2AssetDto update(String id,UpdateRequest request) {
        if(request==null) throw ApiException.badRequest("缺少收藏内容");
        long assetId=CatalogInput.id(id);long expected=CatalogInput.version(request.version());
        Map<Dimension,List<Long>> selected=selection(request.facetIds(),true);
        lockDirectories();lockAsset(assetId,expected);
        Map<Dimension,List<Long>> previous=loadSelections(List.of(assetId)).get(assetId);
        validateSelection(selected,previous);
        AssetDto old=catalog.detail(id);
        catalog.update(id,new CatalogService.UpdateAsset(request.title(),request.kind(),request.description(),request.favorite(),
            old.tags().stream().map(TagDto::id).toList(),expected));
        writeSelection(assetId,selected);
        return detail(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String id,Long version) {lockDirectories();catalog.delete(id,version);}

    @PreAuthorize("hasRole('ADMIN')")
    public V2AssetDto addLocation(String id,CatalogService.LocationChange request) {
        lockDirectories();catalog.addLocation(id,request);return detail(id);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public V2AssetDto updateLocation(String id,String locationId,CatalogService.LocationChange request) {
        lockDirectories();catalog.updateLocation(id,locationId,request);return detail(id);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public V2AssetDto deleteLocation(String id,String locationId,Long version) {
        lockDirectories();catalog.deleteLocation(id,locationId,version);return detail(id);
    }

    private void lockDirectories() {guards.lockFacets();guards.lockTags();}
    private void lockAsset(long id,long version) {
        List<Long> versions=jdbc.queryForList("SELECT version FROM asset WHERE id=? FOR UPDATE",Long.class,id);
        if(versions.isEmpty()) throw ApiException.notFound("收藏不存在");
        if(versions.getFirst()!=version) throw ApiException.conflict("收藏已变更，请刷新后重试");
    }

    private void validateSelection(Map<Dimension,List<Long>> selected,Map<Dimension,List<Long>> previous) {
        Map<Dimension,Map<String,FacetDto>> snapshot=index(facets.snapshot());
        for(Dimension dimension:Dimension.values()) for(long id:selected.get(dimension)) {
            FacetDto entity=snapshot.get(dimension).get(Long.toString(id));
            if(entity==null) throw ApiException.badRequest("所选维度条目不存在");
            if(!previous.get(dimension).contains(id) && !entity.selectable()) throw ApiException.badRequest("不能新增停用条目或归属链已停用的条目");
        }
    }

    private void writeSelection(long assetId,Map<Dimension,List<Long>> selected) {
        for(Dimension dimension:Dimension.values()) {
            jdbc.update("DELETE FROM "+dimension.linkTable()+" WHERE asset_id=?",assetId);
            for(long id:selected.get(dimension)) jdbc.update("INSERT INTO "+dimension.linkTable()+"(asset_id,entity_id) VALUES (?,?)",assetId,id);
        }
    }

    private Map<Long,Map<Dimension,List<Long>>> loadSelections(List<Long> assetIds) {
        Map<Long,Map<Dimension,List<Long>>> result=new LinkedHashMap<>();
        for(long assetId:assetIds) result.put(assetId,emptySelection());
        if(assetIds.isEmpty()) return result;
        for(Dimension dimension:Dimension.values()) jdbc.query("SELECT asset_id,entity_id FROM "+dimension.linkTable()+
            " WHERE asset_id IN ("+placeholders(assetIds.size())+") ORDER BY entity_id",row->{
                result.get(row.getLong("asset_id")).get(dimension).add(row.getLong("entity_id"));
            },assetIds.toArray());
        return result;
    }

    private List<V2AssetDto> enrich(List<AssetDto> assets,Map<Dimension,Map<String,FacetDto>> snapshot) {
        Map<Long,Map<Dimension,List<Long>>> selections=loadSelections(assets.stream().map(a->CatalogInput.id(a.id())).toList());
        List<V2AssetDto> result=new ArrayList<>();
        for(AssetDto asset:assets) {
            Map<Dimension,List<FacetDto>> direct=new EnumMap<>(Dimension.class);
            Map<Dimension,LinkedHashMap<String,MutableEffective>> effective=new EnumMap<>(Dimension.class);
            for(Dimension dimension:Dimension.values()) {
                List<FacetDto> items=selections.get(CatalogInput.id(asset.id())).get(dimension).stream()
                    .map(id->requireSnapshot(snapshot,dimension,Long.toString(id))).toList();
                direct.put(dimension,items);effective.put(dimension,new LinkedHashMap<>());
                for(FacetDto item:items) effective.get(dimension).put(item.id(),new MutableEffective(item));
            }
            for(FacetDto character:direct.get(Dimension.CHARACTER)) if(character.workScopeId()!=null) {
                derive(effective,requireSnapshot(snapshot,Dimension.WORK,character.workScopeId()),character);
            }
            for(FacetDto skin:direct.get(Dimension.SKIN)) {
                FacetDto character=requireSnapshot(snapshot,Dimension.CHARACTER,skin.characterId());
                derive(effective,character,skin);
                if(character.workScopeId()!=null) derive(effective,requireSnapshot(snapshot,Dimension.WORK,character.workScopeId()),skin);
            }
            Map<Dimension,List<V2AssetDto.EffectiveFacet>> projected=new EnumMap<>(Dimension.class);
            for(Dimension dimension:Dimension.values()) projected.put(dimension,effective.get(dimension).values().stream()
                .map(item->V2AssetDto.EffectiveFacet.of(item.facet,List.copyOf(item.reasons))).toList());
            result.add(new V2AssetDto(asset.id(),asset.title(),asset.kind(),asset.description(),asset.favorite(),asset.version(),
                asset.createdAt(),asset.updatedAt(),asset.tags(),asset.locations(),direct,projected));
        }
        return result;
    }

    private static void derive(Map<Dimension,LinkedHashMap<String,MutableEffective>> values,FacetDto target,FacetDto direct) {
        values.get(target.dimension()).computeIfAbsent(target.id(),ignored->new MutableEffective(target)).reasons
            .add(new V2AssetDto.FacetReference(direct.dimension(),direct.id(),direct.name()));
    }
    private static FacetDto requireSnapshot(Map<Dimension,Map<String,FacetDto>> snapshot,Dimension dimension,String id) {
        FacetDto facet=snapshot.get(dimension).get(id);
        if(facet==null) throw new IllegalStateException("Facet reference missing from snapshot");
        return facet;
    }
    private static Map<Dimension,Map<String,FacetDto>> index(Map<Dimension,List<FacetDto>> snapshot) {
        Map<Dimension,Map<String,FacetDto>> result=new EnumMap<>(Dimension.class);
        for(Dimension dimension:Dimension.values()) result.put(dimension,snapshot.getOrDefault(dimension,List.of()).stream()
            .collect(Collectors.toMap(FacetDto::id,item->item, (a,b)->a, LinkedHashMap::new)));
        return result;
    }
    private static Map<Dimension,List<Long>> selection(Map<Dimension,List<String>> values,boolean complete) {
        if(complete && (values==null || values.size()!=Dimension.values().length)) throw ApiException.badRequest("更新需提供完整六维 facetIds");
        Map<Dimension,List<Long>> result=emptySelection();
        for(Dimension dimension:Dimension.values()) {
            if(values!=null && values.containsKey(dimension)) {
                if(values.get(dimension)==null) throw ApiException.badRequest("维度 ID 集合不能为 null");
                result.put(dimension,ids(values.get(dimension)));
            } else if(complete) throw ApiException.badRequest("更新需提供完整六维 facetIds");
        }
        return result;
    }
    private static Map<Dimension,List<Long>> emptySelection() {
        Map<Dimension,List<Long>> result=new EnumMap<>(Dimension.class);
        for(Dimension dimension:Dimension.values()) result.put(dimension,new ArrayList<>());
        return result;
    }
    static List<Long> ids(List<String> values) {
        if(values==null) return List.of();
        LinkedHashSet<Long> result=new LinkedHashSet<>();
        for(String value:values) {
            if(value==null) throw ApiException.badRequest("维度 ID 不能为空");
            for(String item:value.split(",",-1)) result.add(CatalogInput.id(item.strip()));
            if(result.size()>100) throw ApiException.badRequest("每维最多选择100个值");
        }
        return List.copyOf(result);
    }

    private static String effectiveExists(Dimension dimension,List<Long> ids) {
        String direct="EXISTS (SELECT 1 FROM "+dimension.linkTable()+" d WHERE d.asset_id=a.id"+idCondition("d.entity_id",ids)+")";
        if(dimension==Dimension.CHARACTER) return direct+" OR EXISTS (SELECT 1 FROM asset_skin sk JOIN skin s ON s.id=sk.entity_id WHERE sk.asset_id=a.id"+idCondition("s.character_id",ids)+")";
        if(dimension==Dimension.WORK) return direct+
            " OR EXISTS (SELECT 1 FROM asset_character ac JOIN catalog_character c ON c.id=ac.entity_id WHERE ac.asset_id=a.id AND c.work_scope_id IS NOT NULL"+idCondition("c.work_scope_id",ids)+")"+
            " OR EXISTS (SELECT 1 FROM asset_skin ask JOIN skin s ON s.id=ask.entity_id JOIN catalog_character c ON c.id=s.character_id WHERE ask.asset_id=a.id AND c.work_scope_id IS NOT NULL"+idCondition("c.work_scope_id",ids)+")";
        return direct;
    }
    private static String idCondition(String column,List<Long> ids) {return ids==null?"":" AND "+column+" IN ("+placeholders(ids.size())+")";}
    private static void addEffectiveArgs(List<Object> args,Dimension dimension,List<Long> ids) {
        args.addAll(ids);if(dimension==Dimension.CHARACTER) args.addAll(ids);
        if(dimension==Dimension.WORK) {args.addAll(ids);args.addAll(ids);}
    }
    private static String placeholders(int count) {return String.join(",",java.util.Collections.nCopies(count,"?"));}
    private static final class MutableEffective {
        final FacetDto facet;final Set<V2AssetDto.FacetReference> reasons=new LinkedHashSet<>();
        MutableEffective(FacetDto facet) {this.facet=facet;}
    }
    public record AssetQuery(String q,String kind,String sourceId,Boolean favorite,Boolean untagged,int page,int size,
            Map<Dimension,List<String>> facetIds,Map<Dimension,String> modes,Set<Dimension> missing) {}
    public record V2Page(List<V2AssetDto> items,long total,int page,int size) {}
    public record CreateRequest(String title,String kind,String description,Boolean favorite,
            Map<Dimension,List<String>> facetIds,List<CatalogService.LocationInput> locations) {}
    public record UpdateRequest(String title,String kind,String description,Boolean favorite,
            Map<Dimension,List<String>> facetIds,Long version) {}
}
