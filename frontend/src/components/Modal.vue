<script setup lang="ts">
import { nextTick, onBeforeUnmount, watch, ref } from 'vue'
import { X } from 'lucide-vue-next'
const props = withDefaults(defineProps<{ open: boolean; title: string; subtitle?: string; wide?: boolean; busy?: boolean }>(), { wide: false, busy: false })
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement>()
let previousFocus: HTMLElement | null = null
let oldOverflow = ''
function close() { if (!props.busy) emit('close') }
function keyboard(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); close() }
  if (event.key !== 'Tab' || !panel.value) return
  const elements = Array.from(panel.value.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]'))
  const first = elements[0], last = elements[elements.length - 1]
  if (!first || !last) { event.preventDefault(); panel.value.focus(); return }
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
watch(() => props.open, async open => {
  if (open) {
    previousFocus = document.activeElement as HTMLElement
    oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', keyboard)
    await nextTick()
    panel.value?.focus()
  } else {
    document.body.style.overflow = oldOverflow
    document.removeEventListener('keydown', keyboard)
    previousFocus?.focus()
  }
})
onBeforeUnmount(() => { if (props.open) document.body.style.overflow = oldOverflow; document.removeEventListener('keydown', keyboard) })
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="close">
      <section ref="panel" class="modal" :class="{ 'modal-wide': wide }" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1">
        <header class="modal-header"><div><h2>{{ title }}</h2><p v-if="subtitle">{{ subtitle }}</p></div><button class="icon-button" type="button" aria-label="关闭对话框" :disabled="busy" @click="close"><X :size="20" /></button></header>
        <div class="modal-body"><slot /></div>
        <footer v-if="$slots.footer" class="modal-footer"><slot name="footer" /></footer>
      </section>
    </div>
  </Teleport>
</template>
