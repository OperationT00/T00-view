package com.example.mediavault.catalog;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.facet.Dimension;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/assets")
public class V2AssetController {
    private static final Map<Dimension,String> PREFIX=Map.of(Dimension.TYPE,"type",Dimension.WORK,"work",
        Dimension.CHARACTER,"character",Dimension.SKIN,"skin",Dimension.CLOTHING,"clothing",Dimension.CREATOR,"creator");
    private final V2AssetService assets;
    public V2AssetController(V2AssetService assets) {this.assets=assets;}

    @GetMapping public V2AssetService.V2Page list(@RequestParam MultiValueMap<String,String> params) {
        Set<String> allowed=new LinkedHashSet<>(List.of("q","kind","sourceId","favorite","untagged","page","size","missingDimensions"));
        Map<Dimension,List<String>> ids=new EnumMap<>(Dimension.class);
        Map<Dimension,String> modes=new EnumMap<>(Dimension.class);
        for(Dimension dimension:Dimension.values()) {
            String prefix=PREFIX.get(dimension);allowed.add(prefix+"Ids");allowed.add(prefix+"Mode");
            ids.put(dimension,params.getOrDefault(prefix+"Ids",List.of()));
            modes.put(dimension,scalar(params,prefix+"Mode","ALL"));
        }
        if(!allowed.containsAll(params.keySet())) throw ApiException.badRequest("存在不支持的查询参数，旧 tagIds 不能用于六维筛选");
        Set<Dimension> missing=new LinkedHashSet<>();
        for(String value:params.getOrDefault("missingDimensions",List.of())) for(String item:value.split(",",-1)) missing.add(Dimension.parse(item.strip()));
        return assets.list(new V2AssetService.AssetQuery(scalar(params,"q",null),scalar(params,"kind",null),scalar(params,"sourceId",null),
            bool(params,"favorite"),bool(params,"untagged"),integer(params,"page",1),integer(params,"size",20),ids,modes,missing));
    }
    @GetMapping("/{id}") public V2AssetDto detail(@PathVariable String id) {return assets.detail(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public V2AssetDto create(@RequestBody V2AssetService.CreateRequest request) {return assets.create(request);}
    @PutMapping("/{id}") public V2AssetDto update(@PathVariable String id,@RequestBody V2AssetService.UpdateRequest request) {return assets.update(id,request);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id,@RequestParam Long version) {assets.delete(id,version);}
    @PostMapping("/{id}/locations") public V2AssetDto addLocation(@PathVariable String id,@RequestBody CatalogService.LocationChange request) {return assets.addLocation(id,request);}
    @PutMapping("/{id}/locations/{locationId}") public V2AssetDto updateLocation(@PathVariable String id,@PathVariable String locationId,@RequestBody CatalogService.LocationChange request) {return assets.updateLocation(id,locationId,request);}
    @DeleteMapping("/{id}/locations/{locationId}") public V2AssetDto deleteLocation(@PathVariable String id,@PathVariable String locationId,@RequestParam Long version) {return assets.deleteLocation(id,locationId,version);}

    private static String scalar(MultiValueMap<String,String> params,String name,String fallback) {
        List<String> values=params.get(name);
        if(values==null) return fallback;
        if(values.size()!=1) throw ApiException.badRequest("查询参数不能重复："+name);
        return values.getFirst();
    }
    private static int integer(MultiValueMap<String,String> params,String name,int fallback) {
        try {return Integer.parseInt(scalar(params,name,Integer.toString(fallback)));}
        catch(NumberFormatException ex) {throw ApiException.badRequest("分页参数必须为整数");}
    }
    private static Boolean bool(MultiValueMap<String,String> params,String name) {
        String value=scalar(params,name,null);
        if(value==null) return null;
        if(value.equals("true")) return true;if(value.equals("false")) return false;
        throw ApiException.badRequest("布尔查询参数仅允许 true 或 false");
    }
}
