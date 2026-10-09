<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Plus, X, ChevronDown, Link2 } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { dimensions, dimensionNames, dimensionHints, dimensionPaths, emptyFacets, facetLabel, type Dimension, type Facet, type FacetSelection } from '../types'
import FacetPicker from './FacetPicker.vue'
const props = defineProps<{ modelValue: FacetSelection }>()
const emit = defineEmits<{ 'update:modelValue': [value: FacetSelection] }>()
const active = ref<Dimension | null>(null), characterConstraint = ref<Facet | null>(null), workConstraint = ref<Facet | null>(null), parentPicker = ref<'WORK' | 'CHARACTER' | null>(null)
const relations = ref<FacetSelection>(emptyFacets()), deriving = ref(false), relationError = ref('')
const cache = new Map<string, Facet>(); let generation = 0
async function lookup(dimension: Dimension, id: string) { const key = `${dimension}:${id}`; if (cache.has(key)) return cache.get(key)!; const value = await api<Facet>(`/v2/catalog/${dimensionPaths[dimension]}/${id}`); cache.set(key, value); return value }
watch(() => props.modelValue, async selection => {
  const current = ++generation; deriving.value = true; relationError.value = ''
  try {
    const result = emptyFacets(), roles = [...selection.CHARACTER]
    const skinRoles = await Promise.all(selection.SKIN.map(s => lookup('CHARACTER', s.characterId!)))
    for (const role of skinRoles) { if (!roles.some(r => r.id === role.id)) roles.push(role); if (!selection.CHARACTER.some(r => r.id === role.id) && !result.CHARACTER.some(r => r.id === role.id)) result.CHARACTER.push(role) }
    const works = await Promise.all(roles.filter(r => r.workScopeId).map(r => lookup('WORK', r.workScopeId!)))
    for (const work of works) if (!selection.WORK.some(w => w.id === work.id) && !result.WORK.some(w => w.id === work.id)) result.WORK.push(work)
    if (current === generation) relations.value = result
  } catch (e) { if (current === generation) { relations.value = emptyFacets(); relationError.value = errorMessage(e) } } finally { if (current === generation) deriving.value = false }
}, { deep: true, immediate: true })
onBeforeUnmount(() => { generation++ })
const skinGroups = computed(() => {
  const groups = new Map<string, { role: string; skins: Facet[] }>()
  for (const skin of props.modelValue.SKIN) { const key = skin.characterId || skin.id; if (!groups.has(key)) groups.set(key, { role: skin.context.substring(0, skin.context.lastIndexOf(' / ')) || '所属角色', skins: [] }); groups.get(key)!.skins.push(skin) }
  return [...groups.entries()]
})
function update(dimension: Dimension, values: Facet[]) { emit('update:modelValue', { ...props.modelValue, [dimension]: values }) }
function remove(dimension: Dimension, id: string) { update(dimension, props.modelValue[dimension].filter(f => f.id !== id)) }
function open(dimension: Dimension) { active.value = active.value === dimension ? null : dimension; parentPicker.value = null; characterConstraint.value = null; workConstraint.value = null }
function openRoleSkins(role: Facet) { active.value = 'SKIN'; characterConstraint.value = role; workConstraint.value = null; parentPicker.value = null }
function clearRoleSkins(id: string) { update('SKIN', props.modelValue.SKIN.filter(s => s.characterId !== id)) }
</script>

<template>
  <div class="asset-facet-editor">
    <div class="section-line"><h3>六维标签</h3><span class="field-note">均可留空或多选</span></div>
    <p class="field-note">按收藏实际内容选择。皮肤推导其角色和明确原作，服装不自动添加。</p>
    <section v-for="dimension in dimensions" :key="dimension" class="editor-facet-section">
      <div class="section-line"><strong>{{ dimensionNames[dimension] }}</strong><button type="button" class="text-button" @click="open(dimension)"><ChevronDown v-if="active === dimension" :size="14" /><Plus v-else :size="14" />{{ active === dimension ? '收起候选' : '选择标签' }}</button></div>
      <p class="editor-facet-hint">{{ dimensionHints[dimension] }}</p>
      <div v-if="dimension !== 'SKIN'" class="facet-selected"><span v-if="!modelValue[dimension].length" class="facet-blank">未直接标注</span><span v-for="facet in modelValue[dimension]" :key="facet.id" class="facet-chip" :class="{ 'facet-disabled': !facet.selectable }"><span>{{ facetLabel(facet) }}</span><small v-if="!facet.selectable">历史停用</small><button class="chip-remove" type="button" :aria-label="`移除${facetLabel(facet)}`" @click="remove(dimension, facet.id)"><X :size="12" /></button><button v-if="dimension === 'CHARACTER'" class="chip-action" type="button" :aria-label="`选择${facetLabel(facet)}的皮肤`" @click="openRoleSkins(facet)">选皮肤</button></span></div>
      <div v-else class="skin-selection-groups"><span v-if="!skinGroups.length" class="facet-blank">未直接标注</span><div v-for="[roleId, group] in skinGroups" :key="roleId" class="skin-selection-group"><strong>{{ group.role }}</strong><div class="facet-selected"><span v-for="skin in group.skins" :key="skin.id" class="facet-chip" :class="{ 'facet-disabled': !skin.selectable }"><span>{{ skin.name }}</span><small v-if="!skin.selectable">历史停用</small><button class="chip-remove" type="button" :aria-label="`移除${facetLabel(skin)}`" @click="remove('SKIN', skin.id)"><X :size="12" /></button></span></div></div></div>
      <div v-if="relations[dimension].length" class="facet-relations"><span><Link2 :size="12" />来自已选角色 / 皮肤</span><div v-for="facet in relations[dimension]" :key="facet.id"><span>{{ facetLabel(facet) }}</span><button v-if="dimension === 'CHARACTER'" class="text-button" type="button" @click="clearRoleSkins(facet.id)">移除对应皮肤</button></div></div>
      <div v-if="active === dimension" class="editor-facet-picker">
        <div v-if="dimension === 'CHARACTER' || dimension === 'SKIN'" class="facet-candidate-scope"><button type="button" class="text-button" @click="parentPicker = parentPicker === 'WORK' ? null : 'WORK'">{{ workConstraint ? facetLabel(workConstraint) : '按原作缩小候选' }}</button><button v-if="dimension === 'SKIN'" type="button" class="text-button" @click="parentPicker = parentPicker === 'CHARACTER' ? null : 'CHARACTER'">{{ characterConstraint ? facetLabel(characterConstraint) : '按角色缩小候选' }}</button><button v-if="workConstraint || characterConstraint" type="button" class="text-button" @click="workConstraint = null; characterConstraint = null; parentPicker = null">清除范围</button></div>
        <FacetPicker v-if="parentPicker" :dimension="parentPicker" :model-value="parentPicker === 'WORK' ? (workConstraint ? [workConstraint] : []) : (characterConstraint ? [characterConstraint] : [])" single purpose="ASSOCIATE" :work-scope-id="parentPicker === 'CHARACTER' ? workConstraint?.id : undefined" @update:model-value="parentPicker === 'WORK' ? (workConstraint = $event[0] || null, characterConstraint = null, parentPicker = null) : (characterConstraint = $event[0] || null, parentPicker = null)" />
        <FacetPicker v-else :dimension="dimension" :model-value="modelValue[dimension]" purpose="ASSOCIATE" hide-selected :work-scope-id="dimension === 'CHARACTER' || dimension === 'SKIN' ? workConstraint?.id : undefined" :character-id="dimension === 'SKIN' ? characterConstraint?.id : undefined" @update:model-value="update(dimension, $event)" />
      </div>
    </section>
    <p v-if="deriving" class="field-note">正在计算角色与原作关系…</p><p v-else-if="relationError" class="alert warning">关系预览加载失败：{{ relationError }}。保存时仍由服务器校验并计算。</p>
  </div>
</template>
