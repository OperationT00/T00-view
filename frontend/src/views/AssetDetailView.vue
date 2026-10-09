<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Pencil, Trash2, Star, Film, Images, FileBox, HardDrive, Cloud, Plus, Copy, ExternalLink, MapPin, CalendarDays, RotateCw, CircleAlert } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { auth, notify, confirmAction, copyText } from '../state'
import { kindNames, sourceNames, statusNames, locatorNames, formatDate, safeLink, facetIdsOf, type Asset, type Source, type Location, type LocationInput } from '../types'
import AssetEditor from '../components/AssetEditor.vue'
import Modal from '../components/Modal.vue'
import LocationFields from '../components/LocationFields.vue'
import FacetDetails from '../components/FacetDetails.vue'
const route = useRoute(), router = useRouter()
const asset = ref<Asset | null>(null), sources = ref<Source[]>([]), loading = ref(true), error = ref(''), editor = ref(false), deleting = ref(false)
const locationOpen = ref(false), locationTarget = ref<Location | null>(null), locationBusy = ref(false), locationError = ref('')
const locationForm = ref<LocationInput>({ sourceId: '', locatorType: 'LOCAL_PATH', locator: '', note: '' })
const id = computed(() => String(route.params.id))
let loadId = 0
async function load() {
  const request = ++loadId; loading.value = true; error.value = ''
  try { const result = await Promise.all([api<Asset>(`/v2/assets/${id.value}`), api<Source[]>('/sources')]); if (request !== loadId) return; asset.value = result[0]; sources.value = result[1] }
  catch (e) { if (request === loadId) error.value = errorMessage(e) } finally { if (request === loadId) loading.value = false }
}
watch(id, load, { immediate: true })
async function remove() {
  if (!asset.value || !await confirmAction('删除这份收藏？', '只删除收藏目录中的记录、标签关联和位置，不会删除硬盘或网盘中的原文件。', '删除收藏', true)) return
  deleting.value = true
  try { await api(`/v2/assets/${id.value}`, { method: 'DELETE', query: { version: asset.value.version } }); notify('收藏记录已删除'); await router.push('/assets') }
  catch (e) { notify(errorMessage(e), 'error') } finally { deleting.value = false }
}
async function toggleFavorite() {
  const current = asset.value; if (!current) return
  try { await api(`/v2/assets/${id.value}`, { method: 'PUT', body: { title: current.title, kind: current.kind, description: current.description, favorite: !current.favorite, facetIds: facetIdsOf(current.directFacets), version: current.version } }); await load() }
  catch (e) { notify(errorMessage(e), 'error') }
}
function openLocation(location?: Location) {
  locationTarget.value = location || null; locationError.value = ''
  const source = sources.value[0]
  locationForm.value = location ? { sourceId: location.sourceId, locatorType: location.locatorType, locator: location.locator, note: location.note || '' } : { sourceId: source?.id || '', locatorType: source?.type === 'LOCAL_DISK' ? 'LOCAL_PATH' : 'CLOUD_PATH', locator: '', note: '' }
  locationOpen.value = true
}
async function saveLocation() {
  if (!asset.value) return
  locationBusy.value = true; locationError.value = ''
  try { await api(`/v2/assets/${id.value}/locations${locationTarget.value ? `/${locationTarget.value.id}` : ''}`, { method: locationTarget.value ? 'PUT' : 'POST', body: { ...locationForm.value, version: asset.value.version } }); locationOpen.value = false; notify('存放位置已保存'); await load() }
  catch (e) { locationError.value = errorMessage(e) } finally { locationBusy.value = false }
}
async function removeLocation(location: Location) {
  if (!asset.value || !await confirmAction('移除这个位置？', '只移除目录中的位置记录，不会删除该来源上的原文件。', '移除位置', true)) return
  try { await api(`/v2/assets/${id.value}/locations/${location.id}`, { method: 'DELETE', query: { version: asset.value.version } }); notify('位置记录已移除'); await load() }
  catch (e) { notify(errorMessage(e), 'error') }
}
</script>

<template>
  <RouterLink to="/assets" class="back-link"><ArrowLeft :size="16" />返回收藏</RouterLink>
  <div v-if="loading" class="detail-loading" aria-busy="true"><div class="skeleton skeleton-title" /><div class="skeleton skeleton-line" /><div class="skeleton skeleton-panel" /></div>
  <div v-else-if="error" class="content-error"><CircleAlert :size="32" /><h2>暂时无法打开收藏</h2><p>{{ error }}</p><button class="button secondary" @click="load"><RotateCw :size="16" />重新加载</button></div>
  <template v-else-if="asset">
    <div class="detail-heading"><div class="detail-symbol" :class="asset.kind.toLowerCase()"><Film v-if="asset.kind === 'VIDEO'" :size="30" :stroke-width="1.4" /><Images v-else-if="asset.kind === 'IMAGE_SET'" :size="30" :stroke-width="1.4" /><FileBox v-else :size="30" :stroke-width="1.4" /></div><div class="detail-title"><p class="eyebrow">{{ kindNames[asset.kind] }} · COLLECTION ITEM</p><h1>{{ asset.title }}</h1><p><CalendarDays :size="14" />添加于 {{ formatDate(asset.createdAt) }}<span v-if="asset.favorite" class="favorite-inline"><Star :size="13" fill="currentColor" />标星收藏</span></p></div><div v-if="auth.isAdmin.value" class="detail-actions"><button class="button secondary" :class="{ 'favorite-active': asset.favorite }" :aria-pressed="asset.favorite" @click="toggleFavorite"><Star :size="16" :fill="asset.favorite ? 'currentColor' : 'none'" />{{ asset.favorite ? '已标星' : '标星' }}</button><button class="button primary" @click="editor = true"><Pencil :size="16" />编辑收藏</button><button class="icon-button danger-text" aria-label="删除收藏" :disabled="deleting" @click="remove"><Trash2 :size="18" /></button></div></div>
    <div class="detail-layout"><div class="detail-main"><section class="panel detail-description"><div class="section-line"><h2>收藏笔记</h2><span class="subtle-label">NOTES</span></div><p v-if="asset.description" class="preserve-text">{{ asset.description }}</p><p v-else class="empty-copy">还没有为这份收藏写下说明。</p></section><section class="panel locations-panel"><div class="section-line"><div class="section-title"><h2>存放位置</h2><span class="count-badge">{{ asset.locations.length }}</span></div><button v-if="auth.isAdmin.value" class="button secondary small" :disabled="!sources.length" @click="openLocation()"><Plus :size="15" />添加位置</button></div><p class="section-description">同一份收藏的不同副本，都可以记录在这里。</p><div v-if="!asset.locations.length" class="inline-empty"><MapPin :size="28" :stroke-width="1.3" /><h3>还没有记录去处</h3><p>{{ !sources.length && auth.isAdmin.value ? '先创建一个硬盘或网盘来源，再添加存放位置。' : '补充位置后，就知道该去哪里找了。' }}</p><RouterLink v-if="!sources.length && auth.isAdmin.value" to="/sources" class="text-button">前往存储来源 <ExternalLink :size="13" /></RouterLink></div><article v-for="location in asset.locations" :key="location.id" class="location-card"><div class="location-heading"><span class="source-icon"><HardDrive v-if="location.sourceType === 'LOCAL_DISK'" :size="19" /><Cloud v-else :size="19" /></span><div><h3>{{ location.sourceName }}</h3><span>{{ sourceNames[location.sourceType] }} · {{ locatorNames[location.locatorType] }}</span></div><span class="status-badge" :class="location.reportedStatus.toLowerCase()"><span />{{ statusNames[location.reportedStatus] }}</span></div><div class="path-box"><code>{{ location.locator }}</code><button type="button" class="icon-button" aria-label="复制存放位置" @click="copyText(location.locator)"><Copy :size="16" /></button></div><p v-if="location.note" class="location-note preserve-text">{{ location.note }}</p><div class="location-footer"><span>手动报告 · {{ formatDate(location.statusReportedAt, true) }}</span><div><a v-if="location.locatorType === 'WEB_URL' && safeLink(location.locator)" :href="safeLink(location.locator)" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer" class="text-button">打开页面<ExternalLink :size="13" /></a><template v-if="auth.isAdmin.value"><button class="text-button" @click="openLocation(location)">编辑</button><button class="text-button danger-text" @click="removeLocation(location)">移除</button></template></div></div></article></section></div><aside class="detail-aside"><FacetDetails :asset="asset" /><section class="panel information-panel"><h2>条目信息</h2><dl><div><dt>文件形式</dt><dd>{{ kindNames[asset.kind] }}</dd></div><div><dt>创建时间</dt><dd>{{ formatDate(asset.createdAt, true) }}</dd></div><div><dt>最近更新</dt><dd>{{ formatDate(asset.updatedAt, true) }}</dd></div></dl></section><div class="detail-note"><HardDrive :size="19" :stroke-width="1.4" /><p>这里保存的是目录信息，<br />原文件始终留在原处。</p></div></aside></div>
  </template>
  <AssetEditor :open="editor" :asset="asset" :sources="sources" @close="editor = false" @saved="load" />
  <Modal :open="locationOpen" :title="locationTarget ? '编辑存放位置' : '添加存放位置'" :busy="locationBusy" @close="locationOpen = false"><form id="location-form" class="form-stack" @submit.prevent="saveLocation"><div v-if="locationError" class="alert error" role="alert">{{ locationError }}</div><LocationFields v-model="locationForm" :sources="sources" /></form><template #footer><button class="button secondary" :disabled="locationBusy" @click="locationOpen = false">取消</button><button class="button primary" type="submit" form="location-form" :disabled="locationBusy">{{ locationBusy ? '保存中…' : '保存位置' }}</button></template></Modal>
</template>
