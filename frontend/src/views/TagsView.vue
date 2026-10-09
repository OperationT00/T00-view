<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Search, Tags, Pencil, Trash2, RotateCw, ArrowUpRight, CircleAlert, ChevronLeft, ChevronRight, ChevronDown, X, Link2 } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { auth, notify, confirmAction } from '../state'
import { dimensions, dimensionNames, dimensionPaths, dimensionParams, dimensionHints, facetLabel, safeLink, normalizeTagSearch, type Facet, type Dimension, type CreatorAccount, type Page, type Tag } from '../types'
import Modal from '../components/Modal.vue'
import FacetPicker from '../components/FacetPicker.vue'
import LegacyMigration from '../components/LegacyMigration.vue'
import CharacterSkins from '../components/CharacterSkins.vue'
const route = useRoute(), router = useRouter()
const dimension = ref<Dimension>('TYPE'), legacy = computed(() => route.query.tab === 'legacy' && auth.isAdmin.value)
const managementDimensions: Dimension[] = dimensions.filter(item => item !== 'SKIN')
const expandedCharacters = ref<string[]>([]), redirectedSkin = ref(false)
const hasLegacyTags = ref(false)
const rows = ref<Facet[]>([]), loading = ref(true), error = ref(''), search = ref(''), page = ref(1), total = ref(0), size = 20
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const modal = ref(false), target = ref<Facet | null>(null), busy = ref(false), formError = ref(''), name = ref(''), note = ref(''), disambiguation = ref(''), enabled = ref(true), parent = ref<Facet[]>([]), parentOpen = ref(false), parentLoading = ref(false), parentFailed = ref(false), accounts = ref<CreatorAccount[]>([])
let request = 0, debounce: ReturnType<typeof setTimeout> | undefined, opening = 0
watch(() => [route.query.dimension, route.query.tab], ([value]) => {
  opening++; modal.value = false; target.value = null; parent.value = []; parentOpen.value = false
  parentLoading.value = false; parentFailed.value = false; formError.value = ''; name.value = ''; note.value = ''; disambiguation.value = ''; accounts.value = []
  if (value === 'SKIN') { redirectedSkin.value = true; router.replace({ query: { ...route.query, dimension: 'CHARACTER' } }); return }
  dimension.value = managementDimensions.includes(String(value) as Dimension) ? String(value) as Dimension : 'TYPE'
  expandedCharacters.value = []; page.value = 1; search.value = ''; load()
}, { immediate: true })
async function load() {
  const current = ++request; loading.value = true; error.value = ''
  try { const result = await api<Page<Facet>>(`/v2/catalog/${dimensionPaths[dimension.value]}`, { query: { q: normalizeTagSearch(search.value), purpose: 'MANAGE', page: page.value, size } }); if (current !== request) return; rows.value = result.items; total.value = result.total }
  catch (e) { if (current === request) error.value = errorMessage(e) } finally { if (current === request) loading.value = false }
}
function changeDimension(value: Dimension) { router.replace({ query: { dimension: value } }) }
function toggleSkins(character: Facet) { expandedCharacters.value = expandedCharacters.value.includes(character.id) ? expandedCharacters.value.filter(id => id !== character.id) : [...expandedCharacters.value, character.id] }
function onSearch() {
  clearTimeout(debounce); request++; loading.value = true
  debounce = setTimeout(() => { page.value = 1; load() }, 250)
}
function changePage(value: number) { clearTimeout(debounce); page.value = value; load() }
async function open(facet?: Facet) {
  const current = ++opening
  target.value = facet || null; name.value = facet?.name || ''; note.value = facet?.note || ''; disambiguation.value = facet?.disambiguation || ''; enabled.value = facet?.enabled ?? true; parent.value = []; parentOpen.value = false; parentLoading.value = false; parentFailed.value = false; accounts.value = facet?.accounts.map(a => ({ ...a, homepage: a.homepage || '' })) || []; formError.value = ''; modal.value = true
  const parentDimension = dimension.value === 'CHARACTER' ? 'WORK' : dimension.value === 'SKIN' ? 'CHARACTER' : null
  const parentId = dimension.value === 'CHARACTER' ? facet?.workScopeId : facet?.characterId
  if (parentDimension && parentId) {
    parentLoading.value = true
    try { const value = await api<Facet>(`/v2/catalog/${dimensionPaths[parentDimension]}/${parentId}`); if (current === opening) parent.value = [value] }
    catch (e) { if (current === opening) { parentFailed.value = true; formError.value = `原归属无法读取，请关闭后重试：${errorMessage(e)}` } }
    finally { if (current === opening) parentLoading.value = false }
  }
}
async function save() {
  if (parentLoading.value || parentFailed.value) { formError.value = '请等待原归属读取成功后再保存。'; return }
  if (dimension.value === 'SKIN' && !parent.value.length) { formError.value = '皮肤 / 造型必须选择所属角色。'; return }
  busy.value = true; formError.value = ''
  try {
    const body = { name: name.value.trim(), note: note.value, enabled: enabled.value, ...(dimension.value === 'CHARACTER' || dimension.value === 'CREATOR' ? { disambiguation: disambiguation.value } : {}), ...(dimension.value === 'CHARACTER' ? { workScopeId: parent.value[0]?.id || null } : {}), ...(dimension.value === 'SKIN' ? { characterId: parent.value[0]?.id } : {}), ...(dimension.value === 'CREATOR' ? { accounts: accounts.value } : {}), ...(target.value ? { version: target.value.version } : {}) }
    await api(`/v2/catalog/${dimensionPaths[dimension.value]}${target.value ? `/${target.value.id}` : ''}`, { method: target.value ? 'PUT' : 'POST', body }); modal.value = false; notify(target.value ? '标签资料已更新' : '标签资料已创建'); await load()
  } catch (e) { formError.value = errorMessage(e) } finally { busy.value = false }
}
async function toggleEnabled(facet: Facet) {
  if (!await confirmAction(facet.enabled ? '停用这条记录？' : '启用这条记录？', facet.enabled ? '停用后不再提供新增关联，历史收藏中的直接标签和关系补充仍保留并可检索。' : '启用后仍需要其归属链可用，才能被新增关联。', facet.enabled ? '停用' : '启用')) return
  try { await api(`/v2/catalog/${dimensionPaths[dimension.value]}/${facet.id}`, { method: 'PUT', body: { name: facet.name, note: facet.note, enabled: !facet.enabled, version: facet.version, ...(dimension.value === 'CHARACTER' || dimension.value === 'CREATOR' ? { disambiguation: facet.disambiguation } : {}), ...(dimension.value === 'CHARACTER' ? { workScopeId: facet.workScopeId } : {}), ...(dimension.value === 'SKIN' ? { characterId: facet.characterId } : {}), ...(dimension.value === 'CREATOR' ? { accounts: facet.accounts } : {}) } }); notify(facet.enabled ? '记录已停用' : '记录已启用'); await load() } catch (e) { notify(errorMessage(e), 'error') }
}
async function remove(facet: Facet) {
  if (!await confirmAction(`删除“${facet.name}”？`, '仅无直接或间接引用的记录可删除。被收藏、归属关系或迁移映射使用时，请改为停用。', '删除记录', true)) return
  try { await api(`/v2/catalog/${dimensionPaths[dimension.value]}/${facet.id}`, { method: 'DELETE', query: { version: facet.version } }); notify('记录已删除'); await load() } catch (e) { notify(errorMessage(e), 'error') }
}
onBeforeUnmount(() => { request++; opening++; clearTimeout(debounce) })
onMounted(async () => {
  if (!auth.isAdmin.value) return
  try { hasLegacyTags.value = (await api<Page<Tag>>('/tags', { query: { page: 1, size: 1 } })).total > 0 }
  catch { hasLegacyTags.value = true }
})
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">SIX FACETS, CLEAR CONNECTIONS</p><h1>标签整理<span class="heading-dot">.</span></h1><p>角色下管理皮肤与造型；服装和创作者作为独立标签按需复用。</p></div><button v-if="auth.isAdmin.value && !legacy" class="button primary" @click="open()"><Plus :size="18" />新建{{ dimensionNames[dimension] }}</button></div>
  <div class="dimension-tabs" role="tablist" aria-label="标签维度"><button v-for="item in managementDimensions" :key="item" role="tab" :aria-selected="!legacy && item === dimension" :class="{ active: !legacy && item === dimension }" @click="changeDimension(item)">{{ dimensionNames[item] }}</button><button v-if="auth.isAdmin.value && (hasLegacyTags || legacy)" role="tab" :aria-selected="legacy" :class="{ active: legacy }" @click="router.replace({ query: { tab: 'legacy' } })">旧标签迁移</button></div>
  <LegacyMigration v-if="legacy" />
  <template v-else>
    <div v-if="redirectedSkin && dimension === 'CHARACTER'" class="alert warning">皮肤 / 造型已收进所属角色下；展开角色行即可搜索、浏览和管理。</div><div class="info-banner"><Link2 :size="20" /><p>{{ dimension === 'CHARACTER' ? '角色按原作与备注辨认，皮肤 / 造型是该角色下的子标签，点击角色行的管理按钮展开。' : dimensionHints[dimension] + '。' }} 停用保留历史关联，词表管理不会自动给收藏打标。</p></div>
    <section class="panel management-panel"><div class="management-toolbar"><div class="section-title"><h2>{{ dimensionNames[dimension] }}词表</h2><span class="count-badge">{{ total }}</span></div><div class="search-field small"><Search :size="17" /><input v-model="search" type="search" :placeholder="`搜索${dimensionNames[dimension]}或上下文`" :aria-label="`管理列表搜索${dimensionNames[dimension]}`" @input="onSearch" /></div></div>
      <div v-if="loading" class="loading-block" aria-busy="true"><span class="spinner" />正在读取词表…</div><div v-else-if="error" class="content-error"><CircleAlert :size="28" /><p>{{ error }}</p><button class="button secondary" @click="load"><RotateCw :size="16" />重新加载</button></div><div v-else-if="!rows.length" class="empty-state"><div class="empty-icon"><Tags :size="31" :stroke-width="1.3" /></div><h2>{{ search ? '没有匹配的记录' : `还没有${dimensionNames[dimension]}记录` }}</h2><p>{{ search ? '试试名称、原作、角色或辨识备注。' : '由管理员手动建立词表，关联时按具体记录辨认。' }}</p><button v-if="auth.isAdmin.value && !search" class="button primary" @click="open()"><Plus :size="16" />创建第一条记录</button></div>
      <div v-else class="vocab-list"><article v-for="facet in rows" :key="facet.id" class="vocab-row" :class="{ 'character-parent-row': dimension === 'CHARACTER' }"><div class="vocab-content"><h3>{{ facet.name }}<span class="status-badge" :class="facet.enabled ? 'online' : 'offline'"><span />{{ facet.enabled ? '已启用' : '已停用' }}</span><span v-if="facet.enabled && !facet.selectable" class="historical-label">归属链停用</span></h3><p>{{ facetLabel(facet) }}</p><p v-if="facet.note" class="vocab-note preserve-text">{{ facet.note }}</p><div v-if="facet.accounts.length" class="creator-accounts-preview"><span v-for="(account, index) in facet.accounts" :key="index">{{ account.platform }} · {{ account.account }}<a v-if="safeLink(account.homepage)" :href="safeLink(account.homepage)" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer" class="text-button">主页<ArrowUpRight :size="12" /></a></span></div></div><div class="row-actions"><button v-if="dimension === 'CHARACTER'" class="button secondary small character-skins-toggle" :aria-label="`${expandedCharacters.includes(facet.id) ? '收起' : '展开'}${facetLabel(facet)}的皮肤与造型`" :aria-expanded="expandedCharacters.includes(facet.id)" @click="toggleSkins(facet)"><ChevronDown :size="14" :class="{ 'is-expanded': expandedCharacters.includes(facet.id) }" />皮肤 / 造型</button><RouterLink :to="{ path: '/assets', query: { [`${dimensionParams[dimension]}Ids`]: facet.id } }" class="icon-button" :aria-label="`浏览${facetLabel(facet)}的收藏`" title="浏览相关收藏"><ArrowUpRight :size="17" /></RouterLink><template v-if="auth.isAdmin.value"><button class="icon-button" :aria-label="`编辑${facetLabel(facet)}`" @click="open(facet)"><Pencil :size="16" /></button><button class="text-button" @click="toggleEnabled(facet)">{{ facet.enabled ? '停用' : '启用' }}</button><button class="icon-button danger-text" :aria-label="`删除${facetLabel(facet)}`" @click="remove(facet)"><Trash2 :size="16" /></button></template></div><CharacterSkins v-if="dimension === 'CHARACTER' && expandedCharacters.includes(facet.id)" :key="facet.id" :character="facet" /></article></div>
      <div v-if="!loading && !error && total > size" class="pagination management-pagination"><span>第 {{ page }} / {{ pages }} 页 · 共 {{ total }} 条</span><div><button class="button small secondary" :disabled="page <= 1" @click="changePage(page - 1)"><ChevronLeft :size="15" />上一页</button><button class="button small secondary" :disabled="page >= pages" @click="changePage(page + 1)">下一页<ChevronRight :size="15" /></button></div></div>
    </section>
  </template>
  <Modal :open="modal" :title="`${target ? '编辑' : '新建'}${dimensionNames[dimension]}`" :subtitle="dimensionHints[dimension]" :busy="busy" wide @close="modal = false; opening++"><form id="vocab-form" class="form-stack" @submit.prevent="save"><div v-if="formError" class="alert error" role="alert">{{ formError }}</div><div class="form-grid"><label class="field span-2"><span>名称 <b>*</b></span><input v-model="name" required maxlength="128" :aria-label="`${dimensionNames[dimension]}名称`" placeholder="名称不会被自动合并或推测身份" /></label><label v-if="dimension === 'CHARACTER' || dimension === 'CREATOR'" class="field span-2"><span>辨识备注</span><input v-model="disambiguation" maxlength="128" placeholder="同名时，用原作版本、创作者身份或平台备注区分" /></label><div class="field span-2"><span>可用状态</span><label class="checkbox-label"><input v-model="enabled" type="checkbox" />启用，供新增关联选择</label></div></div>
    <div v-if="dimension === 'CHARACTER' || dimension === 'SKIN'" class="field"><span>{{ dimension === 'CHARACTER' ? '所属作品范围（可空）' : '所属角色（必选）' }}</span><div class="section-line parent-selection"><strong>{{ parentLoading ? '正在读取原归属…' : parentFailed ? '读取失败' : parent[0] ? facetLabel(parent[0]) : '未指定' }}</strong><button type="button" class="text-button" :disabled="parentLoading || parentFailed" @click="parentOpen = !parentOpen">{{ parentOpen ? '收起' : '选择归属' }}</button></div><FacetPicker v-if="parentOpen" v-model="parent" :dimension="dimension === 'CHARACTER' ? 'WORK' : 'CHARACTER'" single purpose="ASSOCIATE" /><p v-if="target" class="field-note">已有直接或间接收藏引用时不能改归属；保存时由服务器检查。</p></div>
    <label class="field"><span>说明</span><textarea v-model="note" rows="3" maxlength="8000" placeholder="可选，记录人工确认的信息" /></label>
    <section v-if="dimension === 'CREATOR'" class="creator-accounts-editor"><div class="section-line"><h3>平台账号 / 主页</h3><button type="button" class="button small secondary" @click="accounts.push({ platform: '', account: '', homepage: '' })"><Plus :size="14" />添加账号</button></div><p class="field-note">仅保存身份资料；跨平台归为同一创作者需人工确认。</p><div v-for="(account, index) in accounts" :key="index" class="creator-account-form"><div class="section-line"><span class="eyebrow">账号 {{ index + 1 }}</span><button type="button" class="icon-button danger-text" aria-label="移除平台账号" @click="accounts.splice(index, 1)"><X :size="15" /></button></div><div class="form-grid"><label class="field"><span>平台 <b>*</b></span><input v-model="account.platform" required maxlength="64" placeholder="平台名称" /></label><label class="field"><span>账号 <b>*</b></span><input v-model="account.account" required maxlength="128" placeholder="账号标识，不按昵称合并" /></label><label class="field span-2"><span>主页链接</span><input v-model="account.homepage" type="url" pattern="https://.*" maxlength="2048" placeholder="可选，https://…" /></label></div></div></section>
    </form><template #footer><button class="button secondary" :disabled="busy" @click="modal = false; opening++">取消</button><button class="button primary" type="submit" form="vocab-form" :disabled="busy || parentLoading || parentFailed">{{ busy ? '保存中…' : '保存记录' }}</button></template></Modal>
</template>
