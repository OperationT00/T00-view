<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Search, X, ChevronLeft, ChevronRight, RotateCw, Check, CircleAlert } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { dimensionNames, dimensionPaths, facetLabel, normalizeTagSearch, type Dimension, type Facet, type FacetPurpose, type Page } from '../types'
const props = withDefaults(defineProps<{ modelValue: Facet[]; dimension?: Dimension | null; purpose?: FacetPurpose; single?: boolean; workScopeId?: string; characterId?: string; hideSelected?: boolean; label?: string }>(), { dimension: null, purpose: 'ASSOCIATE', single: false, hideSelected: false })
const emit = defineEmits<{ 'update:modelValue': [value: Facet[]] }>()
const search = ref(''), page = ref(1), rows = ref<Facet[]>([]), total = ref(0), loading = ref(true), error = ref('')
const size = 12, pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let request = 0, debounce: ReturnType<typeof setTimeout> | undefined
async function load() {
  const current = ++request; loading.value = true; error.value = ''
  try {
    const result = await api<Page<Facet>>(props.dimension ? `/v2/catalog/${dimensionPaths[props.dimension]}` : '/v2/catalog/search', { query: { q: normalizeTagSearch(search.value), page: page.value, size, purpose: props.purpose, workScopeId: props.workScopeId, characterId: props.characterId } })
    if (current !== request) return
    rows.value = result.items; total.value = result.total
  } catch (e) { if (current === request) error.value = errorMessage(e) } finally { if (current === request) loading.value = false }
}
watch(() => [props.dimension, props.purpose, props.workScopeId, props.characterId], () => { page.value = 1; load() }, { immediate: true })
function onSearch() { clearTimeout(debounce); debounce = setTimeout(() => { page.value = 1; load() }, 250) }
function searchNow() { clearTimeout(debounce); page.value = 1; load() }
function clearSearch() { clearTimeout(debounce); search.value = ''; page.value = 1; load() }
function selected(facet: Facet) { return props.modelValue.some(f => f.id === facet.id && f.dimension === facet.dimension) }
function remove(facet: Facet) { emit('update:modelValue', props.modelValue.filter(f => !(f.id === facet.id && f.dimension === facet.dimension))) }
function toggle(facet: Facet) { if (selected(facet)) remove(facet); else emit('update:modelValue', props.single ? [facet] : [...props.modelValue, facet]) }
function changePage(value: number) { clearTimeout(debounce); page.value = value; load() }
onBeforeUnmount(() => { request++; clearTimeout(debounce) })
</script>

<template>
  <div class="facet-picker">
    <div class="search-field small"><Search :size="16" /><input v-model="search" type="search" :placeholder="`搜索${dimension ? dimensionNames[dimension] : '六维标签'}名称或上下文`" :aria-label="label || `搜索${dimension ? dimensionNames[dimension] : '六维标签'}`" @input="onSearch" @keydown.enter.prevent="searchNow" /><button v-if="search" class="icon-button" type="button" aria-label="清空候选搜索" @click="clearSearch"><X :size="14" /></button></div>
    <div v-if="!hideSelected && modelValue.length" class="facet-selected"><button v-for="facet in modelValue" :key="`${facet.dimension}:${facet.id}`" class="facet-chip" :class="{ 'facet-disabled': !facet.selectable }" type="button" :aria-label="`移除${facetLabel(facet)}`" @click="remove(facet)"><span v-if="!dimension" class="facet-dimension">{{ dimensionNames[facet.dimension] }}</span>{{ facetLabel(facet) }}<small v-if="!facet.selectable">历史停用</small><X :size="12" /></button></div>
    <div v-if="loading" class="facet-picker-loading" aria-busy="true"><span class="spinner" />正在查找候选…</div>
    <div v-else-if="error" class="facet-picker-error"><CircleAlert :size="17" /><span>{{ error }}</span><button class="text-button" type="button" @click="load"><RotateCw :size="13" />重试</button></div>
    <div v-else class="facet-candidates"><button v-for="facet in rows" :key="`${facet.dimension}:${facet.id}`" type="button" class="facet-candidate" :class="{ selected: selected(facet) }" :aria-pressed="selected(facet)" @click="toggle(facet)"><span class="candidate-check"><Check v-if="selected(facet)" :size="12" /></span><span class="candidate-content"><strong>{{ facetLabel(facet) }}</strong><small><span v-if="!dimension">{{ dimensionNames[facet.dimension] }} · </span>{{ facet.name }}<span v-if="!facet.selectable"> · {{ facet.enabled ? '归属链停用' : '已停用' }}</span></small></span></button><div v-if="!rows.length" class="facet-candidate-empty"><p>{{ search.trim() ? '没有匹配的候选' : '还没有可选的记录' }}</p><span>{{ purpose === 'ASSOCIATE' ? '新增关联仅提供启用且归属可用的记录。' : '试试名称、原作、角色或辨识备注。' }}</span><button v-if="search" class="text-button" type="button" @click="clearSearch">清空搜索</button></div></div>
    <div v-if="!loading && !error" class="facet-picker-pagination"><span>{{ total }} 项 · {{ page }} / {{ pages }}</span><div><button type="button" class="icon-button" :disabled="page <= 1" aria-label="上一页候选" @click="changePage(page - 1)"><ChevronLeft :size="15" /></button><button type="button" class="icon-button" :disabled="page >= pages" aria-label="下一页候选" @click="changePage(page + 1)"><ChevronRight :size="15" /></button></div></div>
  </div>
</template>
