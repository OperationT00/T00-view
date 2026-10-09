<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search, Pencil, RotateCw, CircleAlert, ArrowRight, CheckCircle2, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { notify, confirmAction } from '../state'
import { dimensions, dimensionNames, dimensionPaths, facetLabel, flattenTags, matchesTagSearch, type Tag, type Facet, type LegacyMigration, type MigrationPreview } from '../types'
import FacetPicker from './FacetPicker.vue'
import Modal from './Modal.vue'
const data = ref<LegacyMigration>({ tags: [], mappings: [] }), labels = ref<Record<string, Facet>>({}), loading = ref(true), error = ref(''), search = ref('')
const target = ref<Tag | null>(null), selection = ref<Facet[]>([]), mappingOpen = ref(false), busy = ref(false), formError = ref(''), preview = ref<MigrationPreview | null>(null), previewBusy = ref(false), applying = ref(false), previewPage = ref(1)
const rows = computed(() => flattenTags(data.value.tags).filter(t => matchesTagSearch(t, search.value)))
const previewPages = computed(() => Math.max(1, Math.ceil((preview.value?.assets.length || 0) / 10)))
const previewRows = computed(() => preview.value?.assets.slice((previewPage.value - 1) * 10, previewPage.value * 10) || [])
async function load() {
  loading.value = true; error.value = ''; preview.value = null
  try {
    data.value = await api<LegacyMigration>('/v2/legacy-migration')
    const targets = new Map(data.value.mappings.flatMap(m => m.targets.map(t => [`${t.dimension}:${t.id}`, t] as const)))
    const results = await Promise.all([...targets].map(async ([key, value]) => [key, await api<Facet>(`/v2/catalog/${dimensionPaths[value.dimension]}/${value.id}`)] as const))
    labels.value = Object.fromEntries(results)
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
onMounted(load)
function mapping(tag: Tag) { return data.value.mappings.find(m => m.legacyTagId === tag.id)?.targets || [] }
function open(tag: Tag) { target.value = tag; selection.value = mapping(tag).map(t => labels.value[`${t.dimension}:${t.id}`]).filter((f): f is Facet => !!f); formError.value = ''; mappingOpen.value = true }
async function saveMapping() {
  if (!target.value) return
  busy.value = true; formError.value = ''
  try { await api(`/v2/legacy-migration/mappings/${target.value.id}`, { method: 'PUT', body: { targets: selection.value.map(f => ({ dimension: f.dimension, id: f.id })) } }); mappingOpen.value = false; notify('人工映射已保存，尚未修改收藏'); await load() }
  catch (e) { formError.value = errorMessage(e) } finally { busy.value = false }
}
async function buildPreview() {
  previewBusy.value = true; preview.value = null; error.value = ''
  try { preview.value = await api<MigrationPreview>('/v2/legacy-migration/preview', { method: 'POST', body: {} }); previewPage.value = 1 }
  catch (e) { error.value = errorMessage(e) } finally { previewBusy.value = false }
}
async function apply() {
  if (!preview.value || !await confirmAction('应用已确认的迁移预览？', '仅追加人工映射的六维关联，包含旧直接标签及祖先的映射。原关联和未映射标签保留；若数据发生变化，预览将被拒绝，需要重新生成。', '确认追加关联')) return
  applying.value = true
  try { const result = await api<{ updatedAssets: number }>('/v2/legacy-migration/apply', { method: 'POST', body: { token: preview.value.token } }); preview.value = null; notify(`迁移完成，更新了 ${result.updatedAssets} 份收藏`); await load() }
  catch (e) { error.value = errorMessage(e); preview.value = null } finally { applying.value = false }
}
</script>

<template>
  <div class="info-banner"><ArrowRight :size="20" /><p>旧标签保留原始路径。先建立六维词表、逐条人工映射，再预览并确认追加关联；不会按名称猜测，也不删除旧关联。</p></div>
  <div v-if="error" class="alert error" role="alert"><CircleAlert :size="17" /><span>{{ error }}</span><button class="text-button" @click="load">重试 / 刷新</button></div>
  <section class="panel management-panel"><div class="management-toolbar"><div class="section-title"><h2>旧标签映射</h2><span class="count-badge">{{ flattenTags(data.tags).length }}</span></div><div class="search-field small"><Search :size="16" /><input v-model="search" type="search" placeholder="搜索旧标签路径" aria-label="搜索旧标签路径" /></div></div><div v-if="loading" class="loading-block" aria-busy="true"><span class="spinner" />读取旧标签与映射…</div><div v-else-if="!rows.length" class="inline-empty"><p>{{ data.tags.length ? '没有匹配的旧标签' : '当前没有待映射的旧标签' }}</p></div><div v-else class="migration-map-list"><article v-for="tag in rows" :key="tag.id" class="migration-map-row"><div><strong>{{ tag.path }}</strong><div class="migration-targets"><span v-for="value in mapping(tag)" :key="`${value.dimension}:${value.id}`" class="facet-chip"><small>{{ dimensionNames[value.dimension] }}</small>{{ labels[`${value.dimension}:${value.id}`] ? facetLabel(labels[`${value.dimension}:${value.id}`]!) : `记录 ${value.id}` }}</span><span v-if="!mapping(tag).length" class="facet-blank">未映射 · 保留待归类</span></div></div><button class="button secondary small" @click="open(tag)"><Pencil :size="13" />人工映射</button></article></div></section>
  <div class="migration-actions"><p class="field-note">子标签收藏会参考祖先的人工映射。保存映射只改迁移方案；确认应用才追加收藏关联。</p><button class="button primary" :disabled="loading || previewBusy || applying || !data.tags.length" @click="buildPreview"><RotateCw :size="15" />{{ previewBusy ? '生成预览中…' : '生成迁移预览' }}</button></div>
  <section v-if="preview" class="panel migration-preview"><div class="section-line"><div><h2>待确认预览</h2><p class="section-description">共 {{ preview.total }} 份收藏。以下为准备追加的直接关联，未映射项继续保留。</p></div><button class="button primary" :disabled="applying || !preview.total" @click="apply"><CheckCircle2 :size="16" />{{ applying ? '应用中…' : '确认应用' }}</button></div><article v-for="asset in previewRows" :key="asset.id" class="migration-preview-asset"><RouterLink :to="`/assets/${asset.id}`"><h3>{{ asset.title }}</h3></RouterLink><div v-for="dimension in dimensions" :key="dimension" class="migration-preview-dimension"><template v-if="asset.additions[dimension]?.length"><span>{{ dimensionNames[dimension] }}</span><span v-for="facet in asset.additions[dimension]" :key="facet.id" class="facet-chip">{{ facetLabel(facet) }}</span></template></div><p v-if="!dimensions.some(d => asset.additions[d]?.length)" class="field-note">无需新增关联。</p><div v-if="asset.unmappedTags.length" class="unmapped-preview"><span>未映射：</span><span v-for="tag in asset.unmappedTags" :key="tag.id">{{ tag.path }}</span></div></article><div v-if="previewPages > 1" class="pagination"><span>预览 {{ previewPage }} / {{ previewPages }} 页</span><div><button class="button secondary small" :disabled="previewPage <= 1" @click="previewPage--"><ChevronLeft :size="14" />上一页</button><button class="button secondary small" :disabled="previewPage >= previewPages" @click="previewPage++">下一页<ChevronRight :size="14" /></button></div></div></section>
  <Modal :open="mappingOpen" title="人工指定新维度" :subtitle="target?.path" wide :busy="busy" @close="mappingOpen = false"><div class="form-stack"><div v-if="formError" class="alert error" role="alert">{{ formError }}</div><p class="field-note">一个旧节点可映射到多个维度；清空选择并保存可撤销映射。不会自动新建原作、角色或皮肤。</p><FacetPicker v-if="mappingOpen" v-model="selection" purpose="ASSOCIATE" label="搜索迁移目标六维标签" /></div><template #footer><button class="button secondary" :disabled="busy" @click="mappingOpen = false">取消</button><button class="button primary" :disabled="busy" @click="saveMapping">{{ busy ? '保存中…' : '保存映射' }}</button></template></Modal>
</template>
