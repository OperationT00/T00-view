<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Plus, Search, Pencil, Trash2, RotateCw, ChevronLeft, ChevronRight, ArrowUpRight, CircleAlert } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { auth, notify, confirmAction } from '../state'
import { facetLabel, normalizeTagSearch, type Facet, type Page } from '../types'
import Modal from './Modal.vue'
const props = defineProps<{ character: Facet }>()
const rows = ref<Facet[]>([]), loading = ref(true), error = ref(''), search = ref(''), page = ref(1), total = ref(0)
const size = 10, pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const modal = ref(false), target = ref<Facet | null>(null), busy = ref(false), formError = ref(''), name = ref(''), note = ref(''), enabled = ref(true)
const formId = computed(() => `character-skin-form-${props.character.id}`)
let request = 0, debounce: ReturnType<typeof setTimeout> | undefined
async function load() {
  const current = ++request; loading.value = true; error.value = ''
  try {
    const result = await api<Page<Facet>>('/v2/catalog/skins', { query: { characterId: props.character.id, q: normalizeTagSearch(search.value), purpose: 'MANAGE', page: page.value, size } })
    if (current !== request) return
    rows.value = result.items; total.value = result.total
    if (page.value > pages.value) { page.value = pages.value; load() }
  } catch (e) { if (current === request) error.value = errorMessage(e) } finally { if (current === request) loading.value = false }
}
watch(() => [props.character.id, props.character.version], () => { clearTimeout(debounce); page.value = 1; search.value = ''; load() }, { immediate: true })
function onSearch() { clearTimeout(debounce); debounce = setTimeout(() => { page.value = 1; load() }, 250) }
function searchNow() { clearTimeout(debounce); page.value = 1; load() }
function changePage(value: number) { clearTimeout(debounce); page.value = value; load() }
function open(skin?: Facet) { target.value = skin || null; name.value = skin?.name || ''; note.value = skin?.note || ''; enabled.value = skin?.enabled ?? true; formError.value = ''; modal.value = true }
async function save() {
  busy.value = true; formError.value = ''
  try {
    await api(`/v2/catalog/skins${target.value ? `/${target.value.id}` : ''}`, { method: target.value ? 'PUT' : 'POST', body: { name: name.value.trim(), note: note.value, enabled: enabled.value, characterId: props.character.id, ...(target.value ? { version: target.value.version } : {}) } })
    modal.value = false; notify(target.value ? '皮肤 / 造型已更新' : '已在当前角色下创建皮肤 / 造型'); await load()
  } catch (e) { formError.value = errorMessage(e) } finally { busy.value = false }
}
async function toggleEnabled(skin: Facet) {
  if (!await confirmAction(skin.enabled ? '停用这个皮肤 / 造型？' : '启用这个皮肤 / 造型？', skin.enabled ? '历史收藏与所属角色关系保留，停用后不再提供新增关联。' : '启用后仍需要所属角色与原作可用，才能用于新增关联。', skin.enabled ? '停用' : '启用')) return
  try { await api(`/v2/catalog/skins/${skin.id}`, { method: 'PUT', body: { name: skin.name, note: skin.note, enabled: !skin.enabled, characterId: props.character.id, version: skin.version } }); notify(skin.enabled ? '皮肤 / 造型已停用' : '皮肤 / 造型已启用'); await load() } catch (e) { notify(errorMessage(e), 'error') }
}
async function remove(skin: Facet) {
  if (!await confirmAction(`删除“${skin.name}”？`, '仅没有收藏关联或迁移引用的皮肤 / 造型可删除，所属角色保留。已被使用时请改为停用。', '删除皮肤 / 造型', true)) return
  try { await api(`/v2/catalog/skins/${skin.id}`, { method: 'DELETE', query: { version: skin.version } }); notify('皮肤 / 造型已删除'); await load() } catch (e) { notify(errorMessage(e), 'error') }
}
onBeforeUnmount(() => { request++; clearTimeout(debounce) })
</script>

<template>
  <section class="character-skins" :aria-label="`${facetLabel(character)}下的皮肤与造型`">
    <div class="character-skins-header"><div><h4>皮肤 / 造型 <span class="count-badge">{{ total }}</span></h4><p>仅属于 {{ facetLabel(character) }} 的子标签。</p></div><button v-if="auth.isAdmin.value" class="button secondary small" :disabled="!character.selectable" :title="character.selectable ? '在当前角色下创建，归属自动填写' : '角色或原作已停用，不能新建归属'" :aria-label="`在${facetLabel(character)}下新建皮肤`" @click="open()"><Plus :size="14" />新建皮肤 / 造型</button></div>
    <div class="search-field small"><Search :size="15" /><input v-model="search" type="search" placeholder="搜索当前角色的皮肤 / 造型" :aria-label="`搜索${facetLabel(character)}的皮肤与造型`" @input="onSearch" @keydown.enter.prevent="searchNow" /></div>
    <div v-if="loading" class="facet-picker-loading" aria-busy="true"><span class="spinner" />读取当前角色的造型…</div><div v-else-if="error" class="facet-picker-error"><CircleAlert :size="16" /><span>{{ error }}</span><button class="text-button" @click="load"><RotateCw :size="13" />重试</button></div><div v-else-if="!rows.length" class="character-skins-empty"><p>{{ search.trim() ? '当前角色下没有匹配的皮肤 / 造型' : '这个角色还没有皮肤 / 造型' }}</p><button v-if="search" class="text-button" @click="search = ''; searchNow()">清空搜索</button></div>
    <div v-else class="character-skin-list"><article v-for="skin in rows" :key="skin.id" class="character-skin-row"><div class="character-skin-content"><h5>{{ skin.name }}<span class="status-badge" :class="skin.enabled ? 'online' : 'offline'"><span />{{ skin.enabled ? '已启用' : '已停用' }}</span><span v-if="skin.enabled && !skin.selectable" class="historical-label">归属链停用</span></h5><p>{{ facetLabel(skin) }}</p><p v-if="skin.note" class="preserve-text skin-note">{{ skin.note }}</p></div><div class="row-actions"><RouterLink :to="{ path: '/assets', query: { skinIds: skin.id } }" class="icon-button" :aria-label="`浏览${facetLabel(skin)}的收藏`" title="浏览相关收藏"><ArrowUpRight :size="16" /></RouterLink><template v-if="auth.isAdmin.value"><button class="icon-button" :aria-label="`编辑${facetLabel(skin)}`" @click="open(skin)"><Pencil :size="15" /></button><button class="text-button" @click="toggleEnabled(skin)">{{ skin.enabled ? '停用' : '启用' }}</button><button class="icon-button danger-text" :aria-label="`删除${facetLabel(skin)}`" @click="remove(skin)"><Trash2 :size="15" /></button></template></div></article></div>
    <div v-if="!loading && !error && total > size" class="pagination character-skins-pagination"><span>第 {{ page }} / {{ pages }} 页 · 共 {{ total }} 个造型</span><div><button class="button small secondary" :disabled="page <= 1" aria-label="上一页角色皮肤" @click="changePage(page - 1)"><ChevronLeft :size="14" />上一页</button><button class="button small secondary" :disabled="page >= pages" aria-label="下一页角色皮肤" @click="changePage(page + 1)">下一页<ChevronRight :size="14" /></button></div></div>
    <Modal :open="modal" :title="target ? '编辑皮肤 / 造型' : '新建皮肤 / 造型'" :subtitle="`所属角色：${facetLabel(character)}`" :busy="busy" @close="modal = false"><form :id="formId" class="form-stack" @submit.prevent="save"><div v-if="formError" class="alert error" role="alert">{{ formError }}</div><div class="skin-owner-preview"><span>角色下的子标签</span><strong>{{ facetLabel(character) }}</strong><p>归属自动填写，当前入口不能改到其他角色。</p></div><label class="field"><span>皮肤 / 造型名称 <b>*</b></span><input v-model="name" required maxlength="128" aria-label="皮肤 / 造型名称" placeholder="填写该角色的具体皮肤或造型名称" /></label><label class="field"><span>说明</span><textarea v-model="note" rows="3" maxlength="8000" placeholder="可选，记录人工确认的信息" /></label><label class="checkbox-label"><input v-model="enabled" type="checkbox" />启用，供新增关联选择</label><p class="field-note">收藏关联这个造型时可补充所属角色与明确原作，不会自动添加服装属性。</p></form><template #footer><button class="button secondary" :disabled="busy" @click="modal = false">取消</button><button class="button primary" type="submit" :form="formId" :disabled="busy">{{ busy ? '保存中…' : '保存皮肤 / 造型' }}</button></template></Modal>
  </section>
</template>
