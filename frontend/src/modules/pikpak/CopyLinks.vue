<script setup lang="ts">
import { ref } from 'vue'
import { exportUrl, message, shareUrl, type Share } from './api'

const props = defineProps<{ share?: Share }>()
const busy = ref(false), status = ref(''), manualText = ref('')
async function copy() {
  busy.value = true; status.value = ''; manualText.value = ''
  let text = ''
  try {
    if (props.share) text = shareUrl(props.share)
    else {
      const response = await fetch(exportUrl(), { cache: 'no-store' })
      if (!response.ok) throw new Error('读取链接失败，请重试')
      text = (await response.text()).trimEnd()
    }
    if (!text) throw new Error('暂无可复制的链接')
    if (!navigator.clipboard?.writeText) throw new Error('当前浏览器不支持直接复制')
    await navigator.clipboard.writeText(text)
    status.value = props.share ? '链接已复制' : `已复制 ${text.split(/\r?\n/).length} 条链接，每行一条`
  } catch (e) {
    status.value = text ? '未能写入剪贴板，请选中下方链接后按 Ctrl+C 复制。' : message(e)
    manualText.value = text
  } finally { busy.value = false }
}
</script>

<template>
  <div class="pk-copy">
    <button type="button" :disabled="busy" @click="copy">{{busy ? '正在复制…' : share ? '复制链接' : '复制全部链接'}}</button>
    <span v-if="status" class="pk-muted" role="status">{{status}}</span>
    <textarea v-if="manualText" :value="manualText" readonly aria-label="待复制的分享链接" @focus="($event.target as HTMLTextAreaElement).select()" />
  </div>
</template>

<style scoped>
.pk-copy{display:inline-flex;align-items:center;gap:10px;flex-wrap:wrap;max-width:100%}
.pk-copy textarea{flex-basis:100%;min-width:240px}
</style>
