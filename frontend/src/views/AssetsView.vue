<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Search, SlidersHorizontal, Star, X, ArrowUpRight, Film, Images, FileBox, HardDrive, ChevronLeft, ChevronRight, Tags, FolderOpen, RotateCw, ChevronDown, LibraryBig, CircleAlert } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { auth, notify } from '../state'
import { dimensions, dimensionNames, dimensionPaths, dimensionParams, emptyFacets, facetLabel, facetIdsOf, kindNames, formatDate, type Dimension, type Facet, type FacetMode, type Asset, type Page, type Source } from '../types'
import AssetEditor from '../components/AssetEditor.vue'
import FacetPicker from '../components/FacetPicker.vue'
import Modal from '../components/Modal.vue'
const route = useRoute(), router = useRouter()
const assets = ref<Asset[]>([]), total = ref(0), sources = ref<Source[]>([])
const loading = ref(true), error = ref(''), referenceError = ref(''), editor = ref(false), filterOpen = ref(false)
const q = ref(''), kind = ref(''), sourceId = ref(''), favorite = ref(false), untagged = ref(false), page = ref(1)
const selections = ref(emptyFacets()), modes = ref<Record<Dimension, FacetMode>>({ TYPE: 'ALL', WORK: 'ALL', CHARACTER: 'ALL', SKIN: 'ALL', CLOTHING: 'ALL', CREATOR: 'ALL' }), missing = ref<Dimension[]>([])
const pickerOpen = ref(false), pickerDimension = ref<Dimension | null>(null), labelError = ref('')
const size = 20, totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const allSelected = computed(() => dimensions.flatMap(d => selections.value[d]))
const pickerSelection = computed(() => pickerDimension.value ? selections.value[pickerDimension.value] : allSelected.value)
const hasFilter = computed(() => !!q.value || !!kind.value || !!sourceId.value || !!allSelected.value.length || missing.value.length > 0 || favorite.value || untagged.value)
const filterCount = computed(() => Number(!!kind.value) + Number(!!sourceId.value) + allSelected.value.length + missing.value.length + Number(favorite.value) + Number(untagged.value))
const facetCache = new Map<string, Facet>(); let loadId = 0, hydrateId = 0, debounce: ReturnType<typeof setTimeout> | undefined
function queryFilters() {
  const query: Record<string, string | number | undefined> = { q: q.value.trim() || undefined, kind: kind.value || undefined, sourceId: sourceId.value || undefined, favorite: favorite.value ? 'true' : undefined, untagged: untagged.value ? 'true' : undefined, missingDimensions: missing.value.join(',') || undefined }
  for (const dimension of dimensions) if (selections.value[dimension].length) { const prefix = dimensionParams[dimension]; query[`${prefix}Ids`] = selections.value[dimension].map(f => f.id).join(','); query[`${prefix}Mode`] = modes.value[dimension] }
  return query
}
async function load() {
  const id = ++loadId; loading.value = true; error.value = ''
  try {
    const result = await api<Page<Asset>>('/v2/assets', { query: { ...queryFilters(), page: page.value, size } })
    if (id !== loadId) return
    assets.value = result.items; total.value = result.total
    if (page.value > totalPages.value) changePage(totalPages.value)
  } catch (e) { if (id === loadId) error.value = errorMessage(e) } finally { if (id === loadId) loading.value = false }
}
async function loadSources() { referenceError.value = ''; try { sources.value = await api<Source[]>('/sources') } catch (e) { referenceError.value = errorMessage(e) } }
watch(() => route.query, async query => {
  const current = ++hydrateId; labelError.value = ''
  q.value = String(query.q || ''); kind.value = String(query.kind || ''); sourceId.value = String(query.sourceId || '')
  favorite.value = query.favorite === 'true'; untagged.value = query.untagged === 'true'; page.value = Math.max(1, Number(query.page) || 1)
  missing.value = String(query.missingDimensions || '').split(',').filter((d): d is Dimension => dimensions.includes(d as Dimension))
  const restored = emptyFacets(), requests: Promise<void>[] = []
  for (const dimension of dimensions) {
    const prefix = dimensionParams[dimension]; modes.value[dimension] = query[`${prefix}Mode`] === 'ANY' ? 'ANY' : 'ALL'
    const ids = [...new Set(String(query[`${prefix}Ids`] || '').split(',').filter(Boolean))]
    for (const id of ids) {
      const key = `${dimension}:${id}`, cached = facetCache.get(key)
      const placeholder: Facet = { id, dimension, name: `记录 ${id}`, context: `记录 ${id}`, disambiguation: null, note: null, enabled: true, selectable: true, version: 0, workScopeId: null, characterId: null, accounts: [] }
      restored[dimension].push(cached || placeholder)
      if (!cached) requests.push(api<Facet>(`/v2/catalog/${dimensionPaths[dimension]}/${id}`).then(facet => { facetCache.set(key, facet); const index = restored[dimension].findIndex(f => f.id === id); if (index >= 0) restored[dimension][index] = facet }).catch(e => { if (current === hydrateId) labelError.value = errorMessage(e) }))
    }
  }
  selections.value = restored; load(); await Promise.all(requests)
  if (current === hydrateId) selections.value = { ...restored }
}, { immediate: true })
onMounted(loadSources)
onBeforeUnmount(() => { clearTimeout(debounce); loadId++; hydrateId++ })
function applyFilters() { clearTimeout(debounce); router.replace({ query: queryFilters() }) }
function search() { clearTimeout(debounce); debounce = setTimeout(applyFilters, 300) }
function openPicker(dimension: Dimension | null) { pickerDimension.value = dimension; pickerOpen.value = true }
function updatePicker(values: Facet[]) {
  for (const value of values) facetCache.set(`${value.dimension}:${value.id}`, value)
  if (pickerDimension.value) selections.value = { ...selections.value, [pickerDimension.value]: values }
  else { const result = emptyFacets(); for (const value of values) result[value.dimension].push(value); selections.value = result }
  missing.value = missing.value.filter(d => !selections.value[d].length); if (values.length) untagged.value = false; applyFilters()
}
function removeFacet(facet: Facet) { selections.value = { ...selections.value, [facet.dimension]: selections.value[facet.dimension].filter(f => f.id !== facet.id) }; applyFilters() }
function toggleMissing(dimension: Dimension) { if (missing.value.includes(dimension)) missing.value = missing.value.filter(d => d !== dimension); else { missing.value = [...missing.value, dimension]; selections.value = { ...selections.value, [dimension]: [] } }; applyFilters() }
function clearFilters() { q.value = ''; kind.value = ''; sourceId.value = ''; selections.value = emptyFacets(); missing.value = []; favorite.value = false; untagged.value = false; applyFilters() }
function changePage(value: number) { router.replace({ query: { ...route.query, page: value === 1 ? undefined : String(value) } }) }
function toggleUntagged() { untagged.value = !untagged.value; if (untagged.value) { selections.value = emptyFacets(); missing.value = [] }; applyFilters() }
async function toggleFavorite(asset: Asset) {
  try { await api(`/v2/assets/${asset.id}`, { method: 'PUT', body: { title: asset.title, kind: asset.kind, description: asset.description, favorite: !asset.favorite, facetIds: facetIdsOf(asset.directFacets), version: asset.version } }); await load(); notify(asset.favorite ? '已取消标星' : '已标星收藏') }
  catch (e) { notify(errorMessage(e), 'error') }
}
function cardFacets(asset: Asset): Facet[] { return dimensions.flatMap(d => asset.effectiveFacets[d]).slice(0, 3) }
function saved(asset: Asset) { router.push(`/assets/${asset.id}`) }
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">YOUR PERSONAL LIBRARY</p><h1>我的收藏<span class="heading-dot">.</span></h1><p>用六个维度整理喜欢的内容，记住每个去处。</p></div><button v-if="auth.isAdmin.value" class="button primary" :disabled="!!referenceError" @click="editor = true"><Plus :size="18" />添加收藏</button><span v-else class="read-only-badge">看客 · 只读浏览</span></div>
  <div v-if="referenceError" class="alert error"><span>存储来源加载失败：{{ referenceError }}</span><button class="text-button" @click="loadSources">重试</button></div>
  <div v-if="route.query.tagIds" class="alert warning">旧树标签条件未转成六维；原标签仍在详情中保留，请选择已确认的新维度条件。</div>
  <div v-if="labelError" class="alert warning">部分筛选标签信息无法加载：{{ labelError }}。可移除对应条件后重试。</div>
  <div class="collection-workspace facets-workspace">
    <aside class="collection-sidebar" :class="{ 'mobile-filter-open': filterOpen }">
      <div class="section-line"><h2>浏览目录</h2><button class="icon-button mobile-filter-close" aria-label="收起筛选" @click="filterOpen = false"><X :size="17" /></button><span class="subtle-label">LIBRARY</span></div>
      <button class="browse-row" :class="{ active: !favorite && !untagged }" @click="favorite = false; untagged = false; applyFilters()"><FolderOpen :size="17" /><span>全部收藏</span></button>
      <button class="browse-row" :class="{ active: favorite }" @click="favorite = !favorite; applyFilters()"><Star :size="17" /><span>标星收藏</span></button>
      <button class="browse-row" :class="{ active: untagged }" @click="toggleUntagged"><FileBox :size="17" /><span>完全未标注</span></button>
      <div class="filter-divider" />
      <div class="section-line"><h2>六维筛选</h2><RouterLink to="/tags" class="icon-button" aria-label="浏览六维词表"><Tags :size="16" /></RouterLink></div>
      <p class="filter-hint facet-rule-hint">维度之间同时满足；每维可选全部或任一。</p>
      <section v-for="dimension in dimensions" :key="dimension" class="facet-filter-section">
        <div class="section-line"><button type="button" class="facet-filter-title" @click="openPicker(dimension)">{{ dimensionNames[dimension] }}<Plus :size="13" /></button><select v-model="modes[dimension]" :aria-label="`${dimensionNames[dimension]}匹配模式`" class="facet-mode-select" @change="applyFilters"><option value="ALL">全部</option><option value="ANY">任一</option></select></div>
        <div class="sidebar-facet-chips"><button v-for="facet in selections[dimension]" :key="facet.id" class="facet-chip" :title="facetLabel(facet)" :aria-label="`移除${dimensionNames[dimension]}条件${facetLabel(facet)}`" @click="removeFacet(facet)"><span>{{ facetLabel(facet) }}</span><X :size="11" /></button></div>
        <label class="facet-missing"><input type="checkbox" :checked="missing.includes(dimension)" @change="toggleMissing(dimension)" />该维未标注</label>
      </section>
      <div class="sidebar-tip"><span>关系说明</span><p>角色与原作包含来自皮肤的明确归属；服装仅按实际关联筛选。</p></div>
    </aside>
    <section class="collection-main">
      <div class="collection-toolbar"><div class="search-field"><Search :size="18" /><input v-model="q" type="search" placeholder="搜索收藏标题或说明…" aria-label="搜索标题或说明" @input="search" @keydown.enter="applyFilters" /></div><button class="button secondary filter-toggle" @click="filterOpen = !filterOpen"><SlidersHorizontal :size="17" />筛选<span v-if="filterCount" class="counter">{{ filterCount }}</span></button><div class="select-wrap"><select v-model="kind" aria-label="筛选文件形式" @change="applyFilters"><option value="">全部文件形式</option><option value="VIDEO">视频</option><option value="IMAGE_SET">图集</option><option value="OTHER">其他</option></select><ChevronDown :size="15" /></div><div class="select-wrap source-select"><select v-model="sourceId" aria-label="筛选存储来源" @change="applyFilters"><option value="">全部来源</option><option v-for="source in sources" :key="source.id" :value="source.id">{{ source.name }}</option></select><ChevronDown :size="15" /></div></div>
      <div class="facet-search-launch"><button class="button secondary small" @click="openPicker(null)"><Search :size="15" />搜索标签，添加筛选条件</button><span>候选搜索查标签；上方搜索查收藏内容。</span></div>
      <div v-if="hasFilter" class="active-filters facet-active-filters"><template v-for="dimension in dimensions" :key="dimension"><span v-if="selections[dimension].length" class="active-dimension-label">{{ dimensionNames[dimension] }} · {{ modes[dimension] === 'ALL' ? '全部' : '任一' }}</span><button v-for="facet in selections[dimension]" :key="facet.id" class="filter-chip" @click="removeFacet(facet)">{{ facetLabel(facet) }}<X :size="12" /></button><button v-if="missing.includes(dimension)" class="filter-chip" @click="toggleMissing(dimension)">{{ dimensionNames[dimension] }}未标注<X :size="12" /></button></template><button v-if="favorite" class="filter-chip" @click="favorite = false; applyFilters()"><Star :size="12" />标星<X :size="12" /></button><button v-if="untagged" class="filter-chip" @click="untagged = false; applyFilters()">完全未标注<X :size="12" /></button><button class="text-button clear-filter" @click="clearFilters">清空全部筛选</button></div>
      <div class="results-heading"><p>{{ loading ? '正在查找收藏…' : `共 ${total.toLocaleString()} 份收藏` }}<span v-if="hasFilter && !loading"> · 已筛选</span></p><span><span class="sort-dot" />最近添加</span></div>
      <div v-if="error" class="content-error"><CircleAlert :size="30" :stroke-width="1.3" /><p>{{ error }}</p><button class="button secondary" @click="load"><RotateCw :size="16" />重新加载</button></div>
      <div v-else-if="loading" class="asset-grid" aria-busy="true" aria-label="正在加载收藏"><div v-for="n in 6" :key="n" class="asset-card skeleton-card"><div class="skeleton skeleton-art" /><div class="skeleton skeleton-title" /><div class="skeleton skeleton-line" /><div class="skeleton skeleton-line short" /></div></div>
      <div v-else-if="!assets.length" class="empty-state collection-empty"><div class="empty-icon"><Search v-if="hasFilter" :size="32" :stroke-width="1.3" /><LibraryBig v-else :size="34" :stroke-width="1.3" /></div><p class="eyebrow">{{ hasFilter ? 'NOT FOUND YET' : 'YOUR COLLECTION STARTS HERE' }}</p><h2>{{ hasFilter ? '暂时没有匹配的收藏' : '留一页，给你的第一份收藏' }}</h2><p>{{ hasFilter ? '试试减少条件，或将某个维度从全部切换为任一。' : '记下标题、六维标签和位置，下次想找就能找到。' }}</p><button v-if="hasFilter" class="button secondary" @click="clearFilters">清空筛选</button><button v-else-if="auth.isAdmin.value" class="button primary" :disabled="!!referenceError" @click="editor = true"><Plus :size="17" />添加第一份收藏</button><p v-else class="field-note">等待管理员添加收藏后，即可在这里浏览。</p></div>
      <div v-else class="asset-grid"><article v-for="asset in assets" :key="asset.id" class="asset-card"><RouterLink :to="`/assets/${asset.id}`" class="asset-card-link" :aria-label="`查看${asset.title}`"><div class="asset-card-art" :class="asset.kind.toLowerCase()"><span class="art-circle" /><Film v-if="asset.kind === 'VIDEO'" :size="38" :stroke-width="1.2" /><Images v-else-if="asset.kind === 'IMAGE_SET'" :size="38" :stroke-width="1.2" /><FileBox v-else :size="38" :stroke-width="1.2" /><span class="kind-badge">{{ kindNames[asset.kind] }}</span><ArrowUpRight :size="18" class="card-arrow" /></div><div class="asset-card-body"><h3 :title="asset.title">{{ asset.title }}</h3><p class="asset-summary">{{ asset.description || '还没有添加说明' }}</p><div class="asset-card-tags"><span v-for="facet in cardFacets(asset)" :key="`${facet.dimension}:${facet.id}`" :title="`${dimensionNames[facet.dimension]} · ${facetLabel(facet)}`">{{ facet.name }}</span><span v-if="!cardFacets(asset).length" class="muted-tag">{{ asset.tags.length ? '旧标签待归类' : '未标注' }}</span></div><div class="asset-card-bottom"><span><HardDrive :size="13" />{{ asset.locations.length ? `${asset.locations.length} 个位置` : '位置待补充' }}</span><time>{{ formatDate(asset.createdAt) }}</time></div></div></RouterLink><button v-if="auth.isAdmin.value" class="card-star" :class="{ starred: asset.favorite }" :aria-label="asset.favorite ? '取消标星' : '标星收藏'" :aria-pressed="asset.favorite" @click="toggleFavorite(asset)"><Star :size="17" :fill="asset.favorite ? 'currentColor' : 'none'" /></button><span v-else-if="asset.favorite" class="card-star starred"><Star :size="17" fill="currentColor" /></span></article></div>
      <div v-if="!loading && !error && total > size" class="pagination"><span>第 {{ page }} / {{ totalPages }} 页 · 每页 {{ size }} 条</span><div><button class="button secondary small" :disabled="page <= 1" @click="changePage(page - 1)"><ChevronLeft :size="16" />上一页</button><button class="button secondary small" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页<ChevronRight :size="16" /></button></div></div>
    </section>
  </div>
  <AssetEditor :open="editor" :sources="sources" @close="editor = false" @saved="saved" />
  <Modal :open="pickerOpen" :title="pickerDimension ? `筛选${dimensionNames[pickerDimension]}` : '搜索六维标签'" subtitle="选择具体记录才改变收藏结果；不同维度之间同时满足。停用项仍可用于历史检索。" wide @close="pickerOpen = false"><FacetPicker v-if="pickerOpen" :dimension="pickerDimension" :model-value="pickerSelection" purpose="FILTER" @update:model-value="updatePicker" /><template #footer><button class="button primary" @click="pickerOpen = false">完成选择</button></template></Modal>
</template>