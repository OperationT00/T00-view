<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { shareUrl, type Share } from './api'

const props = defineProps<{ share: Share }>()
const windows = /Win/i.test(navigator.platform) || /Windows/i.test(navigator.userAgent)
// The installed Windows client handles this route without creating a task when add_url is absent.
const clientUrl = 'pikpakapp://mypikpak.com/drive/main_tab'
const url = computed(() => shareUrl(props.share))
const busy = ref(false), status = ref(''), manual = ref(false), ready = ref(false)
watch(url, () => { status.value = ''; manual.value = false; ready.value = false })

function launch() {
  if (!windows) return
  ready.value = true
  status.value = '已请求打开 PikPak。如浏览器询问，请选择打开；进入客户端后按 Ctrl+V 粘贴链接。有提取码的分享需另行输入。'
  try { window.location.assign(clientUrl) }
  catch { status.value = '链接已准备好，但浏览器未能唤起 PikPak。请手动打开 Windows 客户端并粘贴。' }
}

async function copyAndOpen() {
  if (!windows || busy.value) return
  busy.value = true; status.value = ''; manual.value = false; ready.value = false
  const text = url.value
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
    await navigator.clipboard.writeText(text)
    if (text !== url.value) return
    launch()
  } catch {
    manual.value = true
    status.value = '自动复制失败。请选中下方链接，按 Ctrl+C 后点击“已复制，打开 PikPak”。'
  } finally { busy.value = false }
}
</script>

<template>
  <div class="pk-open-client">
    <a class="pk-action pk-primary" :href="url" target="_blank" rel="noopener noreferrer" :aria-label="`打开分享页面：${share.name}（PikPak 网页版，新标签页）`">打开分享页面 ↗</a>
    <span class="pk-muted">PikPak 网页版 · 直接进入此分享</span>
    <details v-if="windows" class="pk-client-options">
      <summary>Windows 客户端</summary>
      <p class="pk-muted">客户端入口仅负责复制与唤起，打开后需要手动粘贴分享链接。</p>
      <button type="button" :disabled="busy" @click="copyAndOpen">{{busy ? '正在复制…' : '复制并打开客户端'}}</button>
    </details>
    <div v-if="status" class="pk-client-help" role="status">
      <p>{{status}}</p>
      <template v-if="manual">
        <textarea :value="url" readonly aria-label="手动复制到 PikPak 的分享链接" @focus="($event.target as HTMLTextAreaElement).select()" />
        <button type="button" @click="manual = false; launch()">已复制，打开 PikPak</button>
      </template>
      <div v-if="ready" class="pk-row">
        <a class="pk-action" :href="clientUrl">再次打开 PikPak</a>
        <a class="pk-action" :href="url" target="_blank" rel="noopener noreferrer">打开官方分享页</a>
        <span class="pk-muted">未打开时，请确认已安装 PikPak，或手动启动客户端。</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pk-open-client{display:inline-flex;flex-wrap:wrap;align-items:center;gap:10px;max-width:100%}
.pk-client-options{flex-basis:100%}.pk-client-options summary{cursor:pointer;color:#627654;font-size:14px}.pk-client-options p{margin:8px 0}
.pk-client-help{flex-basis:100%;padding:12px;border:1px solid #dfe6d8;border-radius:8px;background:#f7f8f3;font-size:14px}
.pk-client-help p{margin:0 0 10px}.pk-client-help textarea{min-width:220px;margin-bottom:10px}
</style>
