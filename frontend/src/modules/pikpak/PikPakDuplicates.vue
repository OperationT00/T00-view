<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import Layout from './PikPakLayout.vue'
import { request, message, size } from './api'
interface Video { shareId: string; shareName: string; shareUrl: string; fileId: string; name: string; sizeBytes: number; folders: { id: string; name: string }[] }
interface Scan { id: string; status: string; startedAt: string; finishedAt: string | null; totalShares: number; completedShares: number; pages: number; videos: number; unknownSize: number; groups: { sizeBytes: number; videos: Video[] }[]; issues: { shareId: string; shareName: string; message: string }[] }
const scan = ref<Scan | null>(null), busy = ref(false), error = ref(''), page = ref(0)
let timer: ReturnType<typeof setTimeout> | undefined, disposed = false
const running = computed(() => scan.value?.status === 'RUNNING')
const groups = computed(() => scan.value?.groups.slice(page.value * 20, (page.value + 1) * 20) || [])
const status = computed(() => ({ RUNNING: '正在扫描', COMPLETED: '扫描完成', PARTIAL: '部分完成 · 结果不完整', CANCELLED: '已停止 · 结果不完整' }[scan.value?.status || ''] || '尚未扫描'))
function location(v: Video) { return { path: `/shares/${v.shareId}`, query: v.folders.length ? { folder: v.folders[v.folders.length - 1]!.id, path: JSON.stringify(v.folders) } : {} } }
function pollLater() { clearTimeout(timer); if (!disposed && running.value) timer = setTimeout(load, 1800) }
async function load() {
  try { scan.value = await request<Scan | null>('/duplicate-scan') || null; error.value = '' }
  catch (e) { error.value = message(e) }
  finally { pollLater() }
}
async function start() {
  busy.value = true; error.value = ''; clearTimeout(timer)
  try { scan.value = await request<Scan>('/duplicate-scan', 'POST'); page.value = 0 }
  catch (e) { error.value = message(e) }
  finally { busy.value = false; pollLater() }
}
async function cancel() {
  if (!scan.value) return
  busy.value = true
  try { await request(`/duplicate-scan/${scan.value.id}/cancel`, 'POST'); await load() }
  catch (e) { error.value = message(e) }
  finally { busy.value = false }
}
function download() {
  if (!scan.value) return
  const url = URL.createObjectURL(new Blob([JSON.stringify(scan.value, null, 2)], { type: 'application/json;charset=utf-8' }))
  const a = document.createElement('a'); a.href = url; a.download = 'pikpak-duplicate-candidates.json'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
onMounted(load)
onUnmounted(() => { disposed = true; clearTimeout(timer) })
</script>
<template><Layout>
  <RouterLink to="/shares" class="pk-action">← 全部在线文件夹</RouterLink>
  <h1 style="margin-top:24px">疑似重复视频</h1>
  <p class="pk-muted">扫描全部已导入的 PikPak 分享及其子文件夹，按精确字节大小查找候选。同大小不代表内容相同，转码后大小不同的视频可能漏检。由你自行决定去留。</p>
  <section class="pk-panel">
    <div class="pk-row"><button class="pk-primary" :disabled="busy || running" @click="start">{{ scan ? '重新扫描全部分享' : '扫描全部分享' }}</button><button v-if="running" :disabled="busy" @click="cancel">停止扫描</button><button :disabled="busy" @click="load">刷新进度</button><button v-if="scan && !running" @click="download">导出扫描结果（JSON）</button></div>
    <p role="status">{{status}}<template v-if="scan"> · 完整扫描 {{scan.completedShares}} / {{scan.totalShares}} 个分享 · 已读取 {{scan.pages}} 页 · {{scan.videos}} 个视频</template></p>
    <p v-if="scan" class="pk-muted">开始时间：{{new Date(scan.startedAt).toLocaleString()}}<template v-if="scan.finishedAt"> · 结束时间：{{new Date(scan.finishedAt).toLocaleString()}}</template></p>
    <p class="pk-muted">仅读取目录信息，不下载视频。关闭页面后扫描继续；服务器暂存最近一次结果，重启后清空，需要留存时请导出。停止扫描会等待当前目录请求结束。</p>
    <p v-if="scan?.unknownSize" class="pk-muted">{{scan.unknownSize}} 个视频大小未知或为零，未参与比较。</p>
  </section>
  <p v-if="error" class="pk-error" role="alert">{{error}}</p>
  <section v-if="scan?.issues.length" class="pk-panel"><h2>未能完整扫描的位置</h2><p v-for="(issue,i) in scan.issues" :key="i"><RouterLink v-if="issue.shareId" :to="`/shares/${issue.shareId}`">{{issue.shareName}}</RouterLink><span v-else>{{issue.shareName}}</span>：{{issue.message}}</p></section>
  <template v-if="scan && !running">
    <h2>{{scan.groups.length}} 组疑似重复</h2>
    <p v-if="!scan.groups.length" class="pk-muted">本次已读取的视频中没有发现大小相同的候选；这不代表不存在内容重复。</p>
    <article v-for="(group,i) in groups" :key="group.sizeBytes" class="pk-panel">
      <h2>第 {{page * 20 + i + 1}} 组 · {{group.videos.length}} 个位置</h2>
      <p class="pk-muted">判断依据：文件大小相同 · {{size(group.sizeBytes)}}（{{group.sizeBytes.toLocaleString()}} 字节）· 待人工确认</p>
      <div v-for="video in group.videos" :key="video.shareId + '/' + video.fileId" class="pk-candidate">
        <strong>{{video.name}}</strong>
        <p>分享：{{video.shareName}}</p>
        <p class="pk-path">位置：/ {{[...video.folders.map(f => f.name), video.name].join(' / ')}}</p>
        <div class="pk-row"><RouterLink class="pk-action" :to="location(video)">定位所在文件夹</RouterLink><a class="pk-action" :href="video.shareUrl" target="_blank" rel="noopener noreferrer">打开原分享 ↗</a></div>
      </div>
    </article>
    <div v-if="scan.groups.length > 20" class="pk-row"><button :disabled="page === 0" @click="page--">上一页</button><span>{{page + 1}} / {{Math.ceil(scan.groups.length / 20)}}</span><button :disabled="(page + 1) * 20 >= scan.groups.length" @click="page++">下一页</button></div>
  </template>
</Layout></template>
<style scoped>.pk-candidate{border-top:1px solid #e1e8da;padding:20px 0;overflow-wrap:anywhere}.pk-candidate p{margin:8px 0}.pk-path{white-space:pre-wrap}</style>
