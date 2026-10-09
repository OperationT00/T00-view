<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown, ChevronRight, Hash } from 'lucide-vue-next'
import type { Tag } from '../types'
defineProps<{ nodes: Tag[]; modelValue: string[]; depth?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
const collapsed = ref<string[]>([])
function toggle(id: string) { collapsed.value = collapsed.value.includes(id) ? collapsed.value.filter(x => x !== id) : [...collapsed.value, id] }
function select(id: string, values: string[]) { emit('update:modelValue', values.includes(id) ? values.filter(x => x !== id) : [...values, id]) }
</script>

<template>
  <ul class="tag-tree" :class="{ 'tree-nested': (depth || 0) > 0 }">
    <li v-for="node in nodes" :key="node.id">
      <div class="tree-row" :class="{ selected: modelValue.includes(node.id) }">
        <button v-if="node.children?.length" type="button" class="tree-expand" :aria-label="`${collapsed.includes(node.id) ? '展开' : '收起'}${node.name}`" :aria-expanded="!collapsed.includes(node.id)" @click="toggle(node.id)"><ChevronRight v-if="collapsed.includes(node.id)" :size="14" /><ChevronDown v-else :size="14" /></button>
        <span v-else class="tree-expand tree-leaf"><Hash :size="12" /></span>
        <label :title="node.path"><input type="checkbox" :checked="modelValue.includes(node.id)" @change="select(node.id, modelValue)" /><span>{{ node.name }}</span></label>
        <span v-if="node.children?.length" class="tree-count">{{ node.children.length }}</span>
      </div>
      <TagTree v-if="node.children?.length && !collapsed.includes(node.id)" :nodes="node.children" :model-value="modelValue" :depth="(depth || 0) + 1" @update:model-value="emit('update:modelValue', $event)" />
    </li>
  </ul>
</template>
