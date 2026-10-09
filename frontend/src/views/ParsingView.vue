<script setup lang="ts">
import { ref, watch } from 'vue'
import { api, errorMessage } from '../api'
import { dimensionNames, type Dimension } from '../types'
interface Match { id: string; name: string; context: string; selectable: boolean }
interface Result { key: string; fileName: string; relativePath: string; kind: string; candidates: { dimension: Dimension; text: string; origin: string; status: string; ownerText: string; matches: Match[] }[]; issues: { code: string; text: string; message: string }[] }
const folder = ref(''), lines = ref(''), busy = ref(false), error = ref(''), results = ref<Result[]>([]), ruleVersion = ref('')
const statuses: Record<string, string> = { MATCHED: '匹配已有', PROPOSED: '建议新建 · 待确认', AMBIGUOUS: '同名待确认', DISABLED: '已停用' }
watch([folder, lines], () => { results.value = []; ruleVersion.value = '' })
async function parse() {
  error.value = ''; results.value = []
  const paths = lines.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean)
  if (!paths.length || paths.length > 100) { error.value = '请输入 1 至 100 行名称或路径。'; return }
  const items = paths.map((path, index) => ({ key: String(index + 1), fileName: path.replaceAll('\\', '/').split('/').at(-1) || '', relativePath: path, classificationFolder: folder.value.trim() }))
  busy.value = true
  try { const data = await api<{ ruleVersion: string; items: Result[] }>('/v2/parsing/preview', { method: 'POST', body: { items } }); results.value = data.items; ruleVersion.value = data.ruleVersion }
  catch (e) { error.value = errorMessage(e) } finally { busy.value = false }
}
function sample() { folder.value = '王者荣耀-公孙离-离恨烟'; lines.value = '博主甲-高跟鞋-黑丝-zw.mp4' }
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">RESOURCE NAME PREVIEW</p><h1>来源解析<span class="heading-dot">.</span></h1><p>先验证名称解析结果，再接入本地硬盘与网盘导入。</p></div></div>
  <section class="panel parsing-form"><form class="form-stack" @submit.prevent="parse">
    <div class="section-line"><h2>解析预览</h2><button type="button" class="text-button" :disabled="busy" @click="sample">填入示例</button></div>
    <label class="field"><span>分类文件夹名称</span><input v-model="folder" :disabled="busy" maxlength="512" placeholder="例如 王者荣耀-公孙离-离恨烟" /><small>填写单个分类文件夹名称：作品-角色-可选皮肤。仅对本批名称使用这份上下文，留空则只解析文件名。</small></label>
    <label class="field"><span>文件名或路径，每行一条</span><textarea v-model="lines" :disabled="busy" rows="6" maxlength="450000" required placeholder="博主甲-高跟鞋-黑丝-zw.mp4" /><small>最多 100 条。路径仅作为文字记录；不会自动选择路径中的某一层作为分类目录。</small></label>
    <p class="field-note">本页仅生成候选，不上传原文件、不创建标签或收藏。zw 仍归入内容类型；建议新建项和提示需要人工确认。</p>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div>
    <button type="submit" class="button primary" :disabled="busy">{{ busy ? '正在解析…' : '解析名称' }}</button>
  </form></section>
  <section v-if="results.length" class="parsing-results" aria-label="解析结果"><p class="field-note">{{ results.length }} 条结果 · 规则 {{ ruleVersion }} · 尚未入库</p>
    <article v-for="item in results" :key="item.key" class="panel parsing-result"><h2>{{ item.fileName }}</h2><p class="field-note">{{ item.relativePath }} · {{ item.kind === 'VIDEO' ? '视频' : '其他 / 待确认形式' }}</p>
      <div class="parsing-candidates"><div v-for="(candidate, index) in item.candidates" :key="index" class="parsing-candidate"><span class="facet-dimension">{{ dimensionNames[candidate.dimension] }}</span><strong>{{ candidate.text }}</strong><span class="status-badge" :class="candidate.status === 'MATCHED' ? 'online' : 'offline'">{{ statuses[candidate.status] }}</span><small>依据：{{ candidate.origin === 'FOLDER' ? '分类文件夹' : '文件名' }}<template v-if="candidate.ownerText"> · 所属：{{ candidate.ownerText }}</template></small><p v-for="match in candidate.matches" :key="match.id" class="field-note">{{ match.context }}</p></div></div>
      <ul v-if="item.issues.length" class="parsing-issues"><li v-for="(issue, index) in item.issues" :key="index"><strong v-if="issue.text">{{ issue.text }}：</strong>{{ issue.message }}</li></ul>
    </article>
  </section>
</template>

<style scoped>
.parsing-form,.parsing-result{padding:24px;max-width:1000px}.parsing-results{display:grid;gap:16px;margin-top:24px}.parsing-result h2{font-size:16px;overflow-wrap:anywhere}.parsing-candidates{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px;margin-top:18px}.parsing-candidate{padding:13px;background:#f5f9ef;border:1px solid #e1e8da;border-radius:7px;overflow-wrap:anywhere}.parsing-candidate strong{display:block;margin:7px 0;font-size:14px}.parsing-candidate small{display:block;margin-top:8px;font-size:11px}.parsing-issues{padding-left:20px;margin-top:18px;color:#80623d;font-size:12px;line-height:1.9}.parsing-result>.field-note{overflow-wrap:anywhere}@media(max-width:600px){.parsing-form,.parsing-result{padding:16px}.parsing-candidates{grid-template-columns:1fr}}
</style>
