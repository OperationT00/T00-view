<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from 'lucide-vue-next'
import { flattenTags, matchesTagSearch, type Tag } from '../types'
const props = defineProps<{ tree: Tag[]; modelValue: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
const search = ref('')
const rows = computed(() => flattenTags(props.tree).filter(t => matchesTagSearch(t, search.value)))
function toggle(id: string) { emit('update:modelValue', props.modelValue.includes(id) ? props.modelValue.filter(x => x !== id) : [...props.modelValue, id]) }
</script>

<template>
  <div class="tag-picker">
    <div class="search-field small"><Search :size="16" /><input v-model="search" type="search" placeholder="搜索标签路径" aria-label="搜索标签路径" /></div>
    <div v-if="tree.length" class="tag-picker-list">
      <label v-for="tag in rows" :key="tag.id" :class="{ checked: modelValue.includes(tag.id) }"><input type="checkbox" :checked="modelValue.includes(tag.id)" @change="toggle(tag.id)" /><span>{{ tag.path }}</span></label>
      <p v-if="!rows.length" class="muted small-text">没有匹配的标签。</p>
    </div>
    <p v-else class="field-note">还没有标签。可以先保存收藏，稍后在“标签整理”中创建。</p>
    <p class="field-note">已选 {{ modelValue.length }} 个，可跨分支选择；只保存直接选中的标签。</p>
  </div>
</template>
