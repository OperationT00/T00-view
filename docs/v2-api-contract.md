# 六维实现接口约定

此文件记录本次实现的跨模块接口；产品规则见 multidimensional-tag-management.md。

维度固定 TYPE, WORK, CHARACTER, SKIN, CLOTHING, CREATOR。路径 types, work-scopes, characters, skins, clothing, creators。

数据库表分别 content_type, work_scope, catalog_character, skin, clothing_tag, creator。收藏关联分别 asset_content_type, asset_work_scope, asset_character, asset_skin, asset_clothing, asset_creator，均 (asset_id, entity_id) 联合主键。catalog_character 有可空 work_scope_id、disambiguation；skin 必填 character_id；creator 有 disambiguation。所有实体有 id,name,normalized_name,note,enabled,version,created_at,updated_at。creator_account(creator_id,platform,account,homepage)。各角色/创作者 normalized_disambiguation 非空；角色生成 work_scope_key=COALESCE(work_scope_id,0)。新增 app_guard FACET_CATALOG。

FacetDto={id,dimension,name,context,disambiguation,note,enabled,selectable,version,workScopeId,characterId,accounts:[{platform,account,homepage}]}。无适用字段为null；accounts始终数组。enabled为自身状态，selectable包含归属链启用状态。

GET /api/v2/catalog/{path}: Page<FacetDto>，q,page=1,size=20(最大100)，可选workScopeId,characterId,purpose=FILTER|ASSOCIATE|MANAGE(默认FILTER)。ASSOCIATE只返回selectable；其他允许历史停用项。
GET /api/v2/catalog/search: 同样分页，额外dimension可选。
归属条件适用性：workScopeId 仅角色／皮肤列表支持，characterId 仅皮肤列表支持；其他维度传入归属条件返回400。皮肤DTO的workScopeId由所属角色推导，只读，写请求只能提交characterId。
GET /api/v2/catalog/{path}/{id}: 单项FacetDto，用于按ID解析已选条件，允许读取历史停用项。
POST/PUT /api/v2/catalog/{path}[/{id}]: {name,disambiguation,note,enabled,workScopeId,characterId,accounts,version}，创建默认enabled=true，PUT必传version。无适用字段拒绝非空值。DELETE ?version；引用保护。
PUT支持enabled停用；任何新归属需存在且启用；被直接或间接收藏、旧标签映射引用时禁止改归属。新账号主页HTTPS无userinfo，不访问网络。

FacetService 公共约定：snapshot() 返回 Map<Dimension,List<FacetDto>>；find(Dimension,String)返回FacetDto或404；validateNewSelection(Dimension,String)验证selectable否则400；Dimension enum 有 table(), linkTable(), path()，parse(String)及fromPath(String)；GuardService.lockFacets()。变更先FACET_CATALOG锁，涉及旧树后TAG_TREE锁，后asset锁，统一顺序。

V2AssetDto沿用原AssetDto基础字段(title,kind,description,favorite,version,createdAt,updatedAt,locations)，tags保留旧直接TagDto[]供待归类展示；新增directFacets和effectiveFacets，六维key永远全有。
directFacets: Record<Dimension,FacetDto[]>。
effectiveFacets: Record<Dimension,EffectiveFacet[]>，EffectiveFacet=FacetDto的全部字段 + derivedFrom:[{dimension,id,name}]；direct项derivedFrom=[]，既直接又推导时仍保留依据，UI以directFacets是否含ID区分。祖先依据指最终直接皮肤/角色。

GET /api/v2/assets支持原q,kind,sourceId,favorite,page,size；untagged=true表示六维直接全无(旧标签不影响)。每维为typeIds/typeMode,workIds/workMode,characterIds/characterMode,skinIds/skinMode,clothingIds/clothingMode,creatorIds/creatorMode(逗号值或重复参数)，mode=ALL默认|ANY；missingDimensions=TYPE,WORK等按有效为空筛选，与同维ids互斥。旧tagIds不支持，不静默猜维度。
POST /api/v2/assets: {title,kind,description,favorite,facetIds:{TYPE:[],WORK:[],CHARACTER:[],SKIN:[],CLOTHING:[],CREATOR:[]},locations:原LocationInput[]}，facetIds省略按空。
PUT /api/v2/assets/{id}: 相同基本字段+facetIds+version，无locations；facetIds必须提供完整六维(防止误覆盖)，只更新六维，保留旧asset_tag。
GET详情/DELETE同原契约。
POST/PUT/DELETE /api/v2/assets/{id}/locations[/{locationId}]沿用原版本/位置字段，返回V2AssetDto。
旧/api/assets、/api/tags只读保留，写入controller默认409 LEGACY_READ_ONLY；测试原行为可vault.legacy-writes-enabled=true，V2内部可调用原CatalogService方法不经过controller。

旧标签迁移(root实现)：GET /api/v2/legacy-migration返回{tags:TagDto[],mappings:[{legacyTagId,targets:[{dimension,id}]}]}。
PUT /api/v2/legacy-migration/mappings/{tagId}: {targets:[{dimension,id}]}，空targets清除映射；旧表和关联保留。
POST /api/v2/legacy-migration/preview: 空body或{}，返回{token,assets:[{id,title,version,additions:Record<Dimension,FacetDto[]>,unmappedTags:TagDto[]}],total}。按旧直接标签及祖先映射，union当前直接项；未映射可见，不猜名称。
POST /api/v2/legacy-migration/apply: {token}，在锁中重新校验预览摘要然后仅追加已确认映射，事务内asset版本递增，返回{updatedAssets}；映射词表、旧标签或asset状态变化导致409要求重预览。token与登录会话绑定、不写GET。移除旧关联不在首版，以免未映射丢失。
legacy_facet_mapping(legacy_tag_id,dimension,entity_id)联合主键，旧tag外键RESTRICT；entity引用服务层校验(按dimension分表)。删除有mapping目标的实体受保护。

所有新GET ADMIN/VIEWER可读；迁移全部仅ADMIN；新POST/PUT/DELETE只有ADMIN，CSRF保留。ID字符串；纯文字渲染；页面不调用外部自动导入。
