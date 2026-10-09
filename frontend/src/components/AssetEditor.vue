<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Trash2 } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { notify } from '../state'
import { emptyFacets, facetIdsOf, type Asset, type Source, type Kind, type LocationInput } from '../types'
import Modal from './Modal.vue'
import AssetFacets from './AssetFacets.vue'
import LocationFields from './LocationFields.vue'
const props = defineProps<{ open: boolean; asset?: Asset | null; sources: Source[] }>()
const emit = defineEmits<{ close: []; saved: [asset: Asset] }>()
const title = ref(''), kind = ref<Kind>('VIDEO'), description = ref(''), favorite = ref(false), facets = ref(emptyFacets()), locations = ref<LocationInput[]>([])
const busy = ref(false), error = ref('')
watch(() => props.open, open => {
  if (!open) return
  title.value = props.asset?.title || ''; kind.value = props.asset?.kind || 'VIDEO'; description.value = props.asset?.description || ''
  favorite.value = props.asset?.favorite || false; facets.value = props.asset ? Object.fromEntries(Object.entries(props.asset.directFacets).map(([key, values]) => [key, [...values]])) as typeof facets.value : emptyFacets(); locations.value = []; error.value = ''
})
function addLocation() {
  const source = props.sources[0]
  locations.value.push({ sourceId: source?.id || '', locatorType: source?.type === 'LOCAL_DISK' ? 'LOCAL_PATH' : 'CLOUD_PATH', locator: '', note: '' })
}
async function save() {
  busy.value = true; error.value = ''
  try {
    const body = { title: title.value.trim(), kind: kind.value, description: description.value, favorite: favorite.value, facetIds: facetIdsOf(facets.value), ...(props.asset ? { version: props.asset.version } : { locations: locations.value }) }
    const result = await api<Asset>(props.asset ? `/v2/assets/${props.asset.id}` : '/v2/assets', { method: props.asset ? 'PUT' : 'POST', body })
    notify(props.asset ? '收藏已更新' : '收藏已添加'); emit('saved', result); emit('close')
  } catch (e) { error.value = errorMessage(e) } finally { busy.value = false }
}
</script>

<template>
  <Modal :open="open" :title="asset ? '编辑收藏' : '添加收藏'" :subtitle="asset ? '更新文字信息和直接关联的标签。' : '记下内容与去处，让收藏更容易找到。'" wide :busy="busy" @close="emit('close')">
    <form id="asset-form" class="form-stack" @submit.prevent="save">
      <div v-if="error" role="alert" class="alert error">{{ error }}</div>
      <div class="form-grid">
        <label class="field span-2"><span>收藏标题 <b>*</b></span><input v-model="title" required maxlength="255" placeholder="给这份收藏起个容易找到的名字" /></label>
        <label class="field"><span>文件形式</span><select v-model="kind"><option value="VIDEO">视频</option><option value="IMAGE_SET">图集</option><option value="OTHER">其他</option></select></label>
        <div class="field"><span>标记</span><label class="checkbox-label favorite-check"><input v-model="favorite" type="checkbox" />标星收藏</label></div>
        <label class="field span-2"><span>说明与备注</span><textarea v-model="description" rows="3" maxlength="8000" placeholder="可选，记录内容、版本或整理笔记" /></label>
      </div>
      <AssetFacets v-if="open" v-model="facets" />
      <p v-if="asset?.tags.length" class="alert warning">这份收藏仍有 {{ asset.tags.length }} 个旧标签；本次保存保留原关联，不会按名称猜测维度。管理员可在标签整理的“旧标签迁移”中人工映射。</p>
      <section v-if="!asset" class="editor-locations">
        <div class="section-line"><h3>存放位置 <span class="muted normal-weight">可稍后补充</span></h3><button type="button" class="button small secondary" :disabled="!sources.length" @click="addLocation"><Plus :size="15" />添加位置</button></div>
        <p v-if="!sources.length" class="field-note">还没有存储来源。保存收藏后，先到“存储来源”创建硬盘或网盘，再补充位置。</p>
        <p v-else-if="!locations.length" class="field-note">可以为同一份内容记录硬盘、网盘等多个位置。</p>
        <div v-for="(location, index) in locations" :key="index" class="location-editor"><div class="section-line"><span class="eyebrow">位置 {{ index + 1 }}</span><button type="button" class="icon-button danger-text" aria-label="移除此位置" @click="locations.splice(index, 1)"><Trash2 :size="16" /></button></div><LocationFields :model-value="location" :sources="sources" @update:model-value="locations[index] = $event" /></div>
      </section>
      <p v-else class="field-note">存放位置可在收藏详情中单独管理。</p>
    </form>
    <template #footer><button type="button" class="button secondary" :disabled="busy" @click="emit('close')">取消</button><button type="submit" form="asset-form" class="button primary" :disabled="busy">{{ busy ? '保存中…' : asset ? '保存修改' : '添加收藏' }}</button></template>
  </Modal>
</template>
