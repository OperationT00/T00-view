<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, HardDrive, Cloud, Pencil, Trash2, RotateCw, CircleAlert, ArrowUpRight, Clock3, Radio } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { auth, notify, confirmAction } from '../state'
import { sourceNames, statusNames, formatDate, type Source, type SourceType, type ReportedStatus } from '../types'
import Modal from '../components/Modal.vue'
const sources = ref<Source[]>([]), loading = ref(true), error = ref(''), modal = ref(false), target = ref<Source | null>(null), busy = ref(false), formError = ref('')
const name = ref(''), type = ref<SourceType>('LOCAL_DISK'), note = ref('')
async function load() { loading.value = true; error.value = ''; try { sources.value = await api<Source[]>('/sources') } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
onMounted(load)
function open(source?: Source) { target.value = source || null; name.value = source?.name || ''; type.value = source?.type || 'LOCAL_DISK'; note.value = source?.note || ''; formError.value = ''; modal.value = true }
async function save() {
  busy.value = true; formError.value = ''
  try { await api(target.value ? `/sources/${target.value.id}` : '/sources', { method: target.value ? 'PUT' : 'POST', body: { name: name.value.trim(), note: note.value, ...(target.value ? { version: target.value.version } : { type: type.value }) } }); modal.value = false; notify(target.value ? '来源已更新' : '来源已创建'); await load() }
  catch (e) { formError.value = errorMessage(e) } finally { busy.value = false }
}
async function remove(source: Source) { if (!await confirmAction(`删除“${source.name}”？`, '仍被收藏位置引用的来源无法删除。删除目录中的来源记录不会访问或删除原文件。', '删除来源', true)) return; try { await api(`/sources/${source.id}`, { method: 'DELETE', query: { version: source.version } }); notify('来源已删除'); await load() } catch (e) { notify(errorMessage(e), 'error') } }
async function status(source: Source, event: Event) {
  const input = event.target as HTMLSelectElement, value = input.value as ReportedStatus
  input.value = source.reportedStatus
  if (!await confirmAction('更新手动报告状态？', `将“${source.name}”标记为“${statusNames[value]}”，并记录当前报告时间。此状态来自你的确认，不是自动探测结果。`, '更新状态')) return
  try { await api(`/sources/${source.id}/reported-status`, { method: 'PUT', body: { status: value, version: source.version } }); notify('状态与报告时间已更新'); await load() } catch (e) { notify(errorMessage(e), 'error') }
}
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">EVERYTHING HAS A PLACE</p><h1>存储来源<span class="heading-dot">.</span></h1><p>记下具体的硬盘与网盘实例，连起收藏的每个去处。</p></div><button v-if="auth.isAdmin.value" class="button primary" @click="open()"><Plus :size="18" />添加来源</button></div>
  <div class="info-banner"><Radio :size="21" :stroke-width="1.5" /><p>来源状态由管理员<strong>手动报告</strong>。标记离线不会移除收藏，也不代表原文件不存在。</p></div>
  <div class="results-heading"><p>{{ sources.length }} 个存储来源</p><span>一块硬盘，一个独立来源</span></div>
  <div v-if="loading" class="loading-block" aria-busy="true"><span class="spinner" />正在加载存储来源…</div><div v-else-if="error" class="content-error"><CircleAlert :size="30" /><p>{{ error }}</p><button class="button secondary" @click="load"><RotateCw :size="16" />重新加载</button></div><section v-else-if="!sources.length" class="panel empty-state"><div class="empty-icon"><HardDrive :size="33" :stroke-width="1.3" /></div><h2>告诉藏页，收藏住在哪里</h2><p>例如“移动硬盘 A”或“我的百度网盘”，无需提供网盘账号密码。</p><button v-if="auth.isAdmin.value" class="button primary" @click="open()"><Plus :size="17" />添加第一个来源</button></section><div v-else class="sources-grid"><article v-for="source in sources" :key="source.id" class="panel source-card"><div class="source-card-top"><span class="source-symbol" :class="source.type.toLowerCase()"><HardDrive v-if="source.type === 'LOCAL_DISK'" :size="26" :stroke-width="1.5" /><Cloud v-else :size="27" :stroke-width="1.5" /></span><span class="status-badge" :class="source.reportedStatus.toLowerCase()"><span />{{ statusNames[source.reportedStatus] }}</span></div><span class="eyebrow">{{ sourceNames[source.type] }}</span><h2>{{ source.name }}</h2><p class="source-note preserve-text">{{ source.note || '暂未添加备注' }}</p><div class="source-report"><Clock3 :size="14" /><span>手动报告 · {{ formatDate(source.statusReportedAt, true) }}</span></div><div class="source-card-bottom"><RouterLink :to="{ path: '/assets', query: { sourceId: source.id } }" class="text-button">浏览收藏<ArrowUpRight :size="14" /></RouterLink><div v-if="auth.isAdmin.value" class="row-actions"><select :value="source.reportedStatus" class="status-select" :aria-label="`更新${source.name}手动状态`" @change="status(source, $event)"><option value="UNKNOWN">未报告</option><option value="ONLINE">可用</option><option value="OFFLINE">离线</option></select><button class="icon-button" :aria-label="`编辑${source.name}`" @click="open(source)"><Pencil :size="16" /></button><button class="icon-button danger-text" :aria-label="`删除${source.name}`" @click="remove(source)"><Trash2 :size="16" /></button></div></div></article></div>
  <Modal :open="modal" :title="target ? '编辑存储来源' : '添加存储来源'" :subtitle="target ? '来源类型创建后保持不变。' : '填写具体设备或网盘的名字，不需要连接账号。'" :busy="busy" @close="modal = false"><form id="source-form" class="form-stack" @submit.prevent="save"><div v-if="formError" class="alert error" role="alert">{{ formError }}</div><label class="field"><span>来源名称 <b>*</b></span><input v-model="name" required maxlength="128" placeholder="例如：移动硬盘 A" /></label><label class="field"><span>来源类型</span><select v-model="type" :disabled="!!target"><option value="LOCAL_DISK">本地硬盘</option><option value="PIKPAK">PikPak</option><option value="BAIDU">百度网盘</option></select></label><label class="field"><span>备注</span><textarea v-model="note" rows="3" maxlength="8000" placeholder="可选，例如容量、存放地点或用途" /></label><p class="field-note">只保存目录信息，请不要在备注中填写密码、Cookie 或访问令牌。</p></form><template #footer><button class="button secondary" :disabled="busy" @click="modal = false">取消</button><button class="button primary" type="submit" form="source-form" :disabled="busy">{{ busy ? '保存中…' : target ? '保存修改' : '添加来源' }}</button></template></Modal>
</template>
