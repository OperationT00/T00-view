<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ShieldCheck, Eye, KeyRound, UserRound } from 'lucide-vue-next'
import { api, errorMessage } from '../api'
import { auth, notify } from '../state'
const router = useRouter()
const oldPassword = ref(''), newPassword = ref(''), repeatedPassword = ref(''), busy = ref(false), error = ref('')
async function save() {
  error.value = ''
  if (newPassword.value !== repeatedPassword.value) { error.value = '两次输入的新密码不一致。'; return }
  if (newPassword.value.length < 12 || new TextEncoder().encode(newPassword.value).length > 72) { error.value = '新密码至少 12 个字符，且 UTF-8 编码不超过 72 字节。'; return }
  busy.value = true
  try { await api('/auth/password', { method: 'PUT', body: { oldPassword: oldPassword.value, newPassword: newPassword.value } }); oldPassword.value = ''; newPassword.value = ''; repeatedPassword.value = ''; auth.clear(); notify('密码已修改，请使用新密码重新登录。', 'info'); await router.replace('/login') }
  catch (e) { error.value = errorMessage(e) } finally { busy.value = false }
}
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">YOUR ACCOUNT</p><h1>我的账号<span class="heading-dot">.</span></h1><p>查看身份，管理自己的登录密码。</p></div></div>
  <div class="account-layout"><section class="panel account-profile"><span class="large-avatar">{{ auth.user.value?.username.slice(0, 1).toUpperCase() }}</span><h2>{{ auth.user.value?.username }}</h2><span class="role-badge"><ShieldCheck v-if="auth.isAdmin.value" :size="15" /><Eye v-else :size="15" />{{ auth.isAdmin.value ? '管理员' : '看客' }}</span><div class="profile-rule" /><p><UserRound :size="17" />{{ auth.isAdmin.value ? '可以浏览和整理收藏，并管理空间账号。' : '可以浏览和搜索收藏、查看资源位置。' }}</p><p class="field-note">账号共用同一个收藏库。用户名创建后不修改。</p></section><section class="panel account-password"><div class="section-line"><h2><KeyRound :size="19" />修改密码</h2></div><p class="section-description">修改成功后，所有已登录设备需要重新登录。</p><form class="form-stack" @submit.prevent="save"><div v-if="error" role="alert" class="alert error">{{ error }}</div><label class="field"><span>当前密码 <b>*</b></span><input v-model="oldPassword" type="password" required autocomplete="current-password" placeholder="验证当前密码" /></label><label class="field"><span>新密码 <b>*</b></span><input v-model="newPassword" type="password" required minlength="12" autocomplete="new-password" placeholder="至少 12 个字符" /><small>至少 12 个字符，UTF-8 编码不超过 72 字节。</small></label><label class="field"><span>确认新密码 <b>*</b></span><input v-model="repeatedPassword" type="password" required minlength="12" autocomplete="new-password" placeholder="再次输入新密码" /></label><div class="form-actions"><button class="button primary" type="submit" :disabled="busy">{{ busy ? '更新中…' : '更新密码' }}</button></div></form></section></div>
</template>
