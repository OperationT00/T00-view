<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LibraryBig, ArrowRight, Eye, EyeOff, LockKeyhole, Tags, HardDrive, Search, CircleAlert } from 'lucide-vue-next'
import { auth } from '../state'
import { errorMessage } from '../api'
const route = useRoute(), router = useRouter()
const username = ref(''), password = ref(''), visible = ref(false), busy = ref(false), error = ref('')
const connectionError = computed(() => typeof route.query.error === 'string' ? route.query.error : '')
async function login() {
  busy.value = true; error.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    password.value = ''
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//') && !route.query.redirect.startsWith('/login') ? route.query.redirect : '/assets'
    await router.replace(redirect)
  } catch (e) { error.value = errorMessage(e) } finally { busy.value = false }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story">
      <div class="brand login-brand"><span class="brand-mark"><LibraryBig :size="28" :stroke-width="1.7" /></span><span><strong>藏页<span class="brand-dot">.</span></strong><small>PRIVATE COLLECTION</small></span></div>
      <div class="story-content"><p class="eyebrow">A PLACE FOR YOUR FAVORITES</p><h1>每一份喜欢，<br />都有迹可循。</h1><p class="story-description">把散落在硬盘与网盘的收藏，<br />整理成属于你的有序目录。</p><div class="story-illustration" aria-hidden="true"><div class="illustration-grid" /><div class="floating-tag"><Tags :size="18" /><span>按标签，串起收藏</span></div><div class="illustration-folder"><div class="folder-tab" /><LibraryBig :size="54" :stroke-width="1" /><span>MY COLLECTION</span><div class="folder-rule" /></div><div class="floating-source"><HardDrive :size="20" /><span>记住每一个去处</span><span class="status-dot" /></div><div class="illustration-star">✳</div></div><div class="story-features"><span><Tags :size="16" />层级标签</span><span><HardDrive :size="16" />多源位置</span><span><Search :size="16" />快速查找</span></div></div>
      <p class="story-footer">少一点翻找，多一点喜欢。</p>
    </section>
    <section class="login-form-panel"><div class="login-card"><span class="login-lock"><LockKeyhole :size="23" :stroke-width="1.5" /></span><p class="eyebrow">WELCOME BACK</p><h2>打开你的收藏空间</h2><p class="login-intro">使用管理员为你创建的账号登录。</p><form class="form-stack" @submit.prevent="login"><div v-if="error || connectionError" class="alert error" role="alert"><CircleAlert :size="17" /><span>{{ error || connectionError }}</span></div><label class="field"><span>用户名</span><input v-model="username" required maxlength="64" autocomplete="username" placeholder="输入用户名" autofocus /></label><label class="field"><span>密码</span><div class="password-field"><input v-model="password" :type="visible ? 'text' : 'password'" required autocomplete="current-password" placeholder="输入密码" /><button type="button" class="icon-button" :aria-label="visible ? '隐藏密码' : '显示密码'" @click="visible = !visible"><EyeOff v-if="visible" :size="18" /><Eye v-else :size="18" /></button></div></label><button class="button primary login-submit" :disabled="busy" type="submit">{{ busy ? '正在登录…' : '进入藏页' }}<ArrowRight :size="18" /></button></form><p class="login-hint"><LockKeyhole :size="13" />仅限已授权账号，未登录无法浏览</p></div><span class="login-copyright">藏页 · 个人收藏目录</span></section>
  </div>
</template>
