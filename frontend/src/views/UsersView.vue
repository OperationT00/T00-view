<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, Users, ShieldCheck, Eye, KeyRound, RotateCw, CircleAlert } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { auth, notify, confirmAction } from '../state'
import type { User, Role } from '../types'
import Modal from '../components/Modal.vue'
const users = ref<User[]>([]), loading = ref(true), error = ref(''), modal = ref(false), target = ref<User | null>(null), busy = ref(false), formError = ref('')
const username = ref(''), password = ref(''), role = ref<Role>('VIEWER')
async function load() { loading.value = true; error.value = ''; try { users.value = await api<User[]>('/admin/users') } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
onMounted(load)
function open(user?: User) { target.value = user || null; username.value = ''; password.value = ''; role.value = 'VIEWER'; formError.value = ''; modal.value = true }
async function save() {
  if (password.value.length < 12 || new TextEncoder().encode(password.value).length > 72) { formError.value = '密码至少 12 个字符，且 UTF-8 编码不超过 72 字节。'; return }
  busy.value = true; formError.value = ''
  try {
    await api(target.value ? `/admin/users/${target.value.id}/reset-password` : '/admin/users', { method: 'POST', body: target.value ? { password: password.value, version: target.value.version } : { username: username.value.trim(), password: password.value, role: role.value } })
    modal.value = false; password.value = ''; notify(target.value ? '密码已重置，旧会话已失效' : '账号已创建'); await load()
  } catch (e) { formError.value = errorMessage(e) } finally { busy.value = false }
}
async function changeRole(user: User, event: Event) {
  const input = event.target as HTMLSelectElement, next = input.value as Role; input.value = user.role
  if (next === user.role || !await confirmAction('调整账号角色？', `将“${user.username}”调整为${next === 'ADMIN' ? '管理员，可修改收藏并管理账号' : '看客，只能浏览收藏'}。该账号已有会话会失效。`, '调整角色')) return
  try { await api(`/admin/users/${user.id}/role`, { method: 'PUT', body: { role: next, version: user.version } }); notify('角色已更新'); await load() } catch (e) { notify(errorMessage(e), 'error') }
}
async function changeStatus(user: User) {
  if (!await confirmAction(user.enabled ? '停用这个账号？' : '启用这个账号？', user.enabled ? `“${user.username}”将无法登录，已有会话会失效。共享收藏会保留。` : `“${user.username}”将可以重新登录并按当前角色访问收藏。`, user.enabled ? '停用账号' : '启用账号', user.enabled)) return
  try { await api(`/admin/users/${user.id}/status`, { method: 'PUT', body: { enabled: !user.enabled, version: user.version } }); notify(user.enabled ? '账号已停用' : '账号已启用'); await load() } catch (e) { notify(errorMessage(e), 'error') }
}
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">A SHARED SPACE, CLEAR PERMISSIONS</p><h1>账号管理<span class="heading-dot">.</span></h1><p>管理员负责整理，看客安心浏览同一个收藏库。</p></div><button class="button primary" @click="open()"><Plus :size="18" />创建账号</button></div>
  <div class="role-summary"><div><ShieldCheck :size="22" /><div><strong>管理员</strong><p>浏览与修改收藏，管理标签、来源和账号。</p></div></div><div><Eye :size="22" /><div><strong>看客</strong><p>浏览、搜索和查看位置；可修改自己的密码。</p></div></div></div>
  <section class="panel management-panel"><div class="management-toolbar"><div class="section-title"><h2>空间成员</h2><span class="count-badge">{{ users.length }}</span></div><span class="muted small-text">至少保留一个启用的管理员</span></div><div v-if="loading" class="loading-block" aria-busy="true"><span class="spinner" />正在加载账号…</div><div v-else-if="error" class="content-error"><CircleAlert :size="28" /><p>{{ error }}</p><button class="button secondary" @click="load"><RotateCw :size="16" />重新加载</button></div><div v-else-if="!users.length" class="inline-empty"><Users :size="28" /><p>还没有账号记录。</p></div><div v-else class="table-scroll"><table class="users-table"><thead><tr><th>成员</th><th>角色</th><th>状态</th><th class="align-right">操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.id"><td><div class="user-cell"><span class="avatar" :class="{ 'viewer-avatar': user.role === 'VIEWER' }">{{ user.username.slice(0, 1).toUpperCase() }}</span><div><strong>{{ user.username }}</strong><span v-if="user.id === auth.user.value?.id" class="self-badge">当前账号</span></div></div></td><td><select class="inline-select" :value="user.role" :aria-label="`调整${user.username}角色`" @change="changeRole(user, $event)"><option value="ADMIN">管理员</option><option value="VIEWER">看客</option></select></td><td><span class="status-badge" :class="user.enabled ? 'online' : 'offline'"><span />{{ user.enabled ? '已启用' : '已停用' }}</span></td><td><div class="row-actions"><button class="text-button" @click="open(user)"><KeyRound :size="14" />重置密码</button><button class="text-button" :class="user.enabled ? 'danger-text' : ''" @click="changeStatus(user)">{{ user.enabled ? '停用' : '启用' }}</button></div></td></tr></tbody></table></div></section>
  <Modal :open="modal" :title="target ? '重置账号密码' : '创建账号'" :subtitle="target ? `为“${target.username}”设置新密码，所有旧会话将失效。` : '账号共用收藏库，默认创建为只读看客。'" :busy="busy" @close="modal = false; password = ''"><form id="user-form" class="form-stack" @submit.prevent="save"><div v-if="formError" class="alert error" role="alert">{{ formError }}</div><label v-if="!target" class="field"><span>用户名 <b>*</b></span><input v-model="username" required maxlength="64" autocomplete="off" placeholder="用于登录，创建后不可修改" /></label><label v-if="!target" class="field"><span>账号角色</span><select v-model="role"><option value="VIEWER">看客 · 只读浏览</option><option value="ADMIN">管理员 · 可修改与管理账号</option></select></label><label class="field"><span>{{ target ? '新密码' : '初始密码' }} <b>*</b></span><input v-model="password" required minlength="12" type="password" autocomplete="new-password" placeholder="至少 12 个字符" /><small>至少 12 个字符，UTF-8 编码不超过 72 字节。</small></label></form><template #footer><button class="button secondary" :disabled="busy" @click="modal = false; password = ''">取消</button><button class="button primary" type="submit" form="user-form" :disabled="busy">{{ busy ? '保存中…' : target ? '重置密码' : '创建账号' }}</button></template></Modal>
</template>
