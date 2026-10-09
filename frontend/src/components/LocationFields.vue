<script setup lang="ts">
import { computed } from 'vue'
import type { Source, LocationInput, LocatorType } from '../types'
import { locatorNames, sourceNames } from '../types'
const props = defineProps<{ modelValue: LocationInput; sources: Source[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: LocationInput] }>()
const selected = computed(() => props.sources.find(s => s.id === props.modelValue.sourceId))
const types = computed<LocatorType[]>(() => selected.value?.type === 'LOCAL_DISK' ? ['LOCAL_PATH'] : ['CLOUD_PATH', 'WEB_URL'])
function update<K extends keyof LocationInput>(key: K, value: LocationInput[K]) { emit('update:modelValue', { ...props.modelValue, [key]: value }) }
function changeSource(event: Event) {
  const sourceId = (event.target as HTMLSelectElement).value
  const source = props.sources.find(s => s.id === sourceId)
  emit('update:modelValue', { ...props.modelValue, sourceId, locatorType: source?.type === 'LOCAL_DISK' ? 'LOCAL_PATH' : 'CLOUD_PATH' })
}
</script>

<template>
  <div class="form-grid">
    <label class="field"><span>存储来源 <b>*</b></span><select :value="modelValue.sourceId" required @change="changeSource"><option value="" disabled>选择来源</option><option v-for="source in sources" :key="source.id" :value="source.id">{{ source.name }} · {{ sourceNames[source.type] }}</option></select></label>
    <label class="field"><span>位置类型 <b>*</b></span><select :value="modelValue.locatorType" required @change="update('locatorType', ($event.target as HTMLSelectElement).value as LocatorType)"><option v-for="type in types" :key="type" :value="type">{{ locatorNames[type] }}</option></select></label>
    <label class="field span-2"><span>{{ modelValue.locatorType === 'WEB_URL' ? 'HTTPS 原始页面链接' : '存放路径' }} <b>*</b></span><input :value="modelValue.locator" :type="modelValue.locatorType === 'WEB_URL' ? 'url' : 'text'" :pattern="modelValue.locatorType === 'WEB_URL' ? 'https://.*' : undefined" :placeholder="modelValue.locatorType === 'WEB_URL' ? 'https://…' : modelValue.locatorType === 'LOCAL_PATH' ? '例如 D:\\收藏\\目录' : '例如 /我的收藏/目录'" required maxlength="4096" @input="update('locator', ($event.target as HTMLInputElement).value)" /><small>仅记录位置，不读取、移动或上传原文件。页面链接请填写稳定的 HTTPS 地址。</small></label>
    <label class="field span-2"><span>位置备注</span><textarea :value="modelValue.note" rows="2" maxlength="8000" placeholder="可选，例如文件名、提取说明" @input="update('note', ($event.target as HTMLTextAreaElement).value)" /></label>
  </div>
</template>
