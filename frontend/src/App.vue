<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LibraryBig, LayoutGrid, Tags, HardDrive, Users, UserRound, LogOut, Menu, X, ArrowUpRight, CheckCircle2, CircleAlert, Info, ChevronRight, ShieldCheck, Eye } from 'lucide-vue-next'
import { auth, toasts, dismissToast, confirmation, answerConfirmation, notify } from './state'
import { errorMessage } from './api'
import Modal from './components/Modal.vue'
const route = useRoute(), router = useRouter()
const mobileOpen = ref(false), loggingOut = ref(false)
const currentTitle = computed(() => String(route.meta.title || '我的收藏'))
const navigation = computed(() => [
  { to: '/assets', label: '我的收藏', icon: LayoutGrid, text: 'COLLECTION' },
  { to: '/tags', label: '标签整理', icon: Tags, text: 'TAGS' },
  { to: '/sources', label: '存储来源', icon: HardDrive, text: 'SOURCES' },
  { to: '/shares', label: 'pikpak', icon: LayoutGrid, text: 'PIKPAK' },
  ...(auth.isAdmin.value ? [{ to: '/parsing', label: '来源解析', icon: Tags, text: 'PARSING' }] : []),
  ...(auth.isAdmin.value ? [{ to: '/users', label: '账号管理', icon: Users, text: 'ACCOUNTS' }] : []),
])
watch(() => route.fullPath, () => { mobileOpen.value = false })
watch(auth.user, user => { if (!user && route.name !== 'login' && !route.meta.public) router.replace({ name: 'login', query: { redirect: route.fullPath } }) })
async function logout() { loggingOut.value = true; try { await auth.logout(); await router.replace('/login') } catch (e) { notify(errorMessage(e), 'error') } finally { loggingOut.value = false } }
</script>

<template>
  <RouterView v-if="route.name === 'login'" />
  <div v-else class="app-shell">
    <button v-if="mobileOpen" class="sidebar-overlay" aria-label="收起导航" @click="mobileOpen = false" />
    <aside class="sidebar" :class="{ 'is-open': mobileOpen }">
      <RouterLink to="/assets" class="brand"><span class="brand-mark"><LibraryBig :size="25" :stroke-width="1.7" /></span><span><strong>藏页<span class="brand-dot">.</span></strong><small>给收藏一个位置</small></span></RouterLink>
      <button class="icon-button mobile-close" aria-label="收起导航" @click="mobileOpen = false"><X :size="20" /></button>
      <div class="nav-label">我的资料库 <span>WORKSPACE</span></div>
      <nav aria-label="主导航"><RouterLink v-for="item in navigation" :key="item.to" :to="item.to" class="nav-item" :class="{ active: route.path.startsWith(item.to) }"><component :is="item.icon" :size="19" :stroke-width="1.7" /><span>{{ item.label }}</span><ChevronRight :size="14" class="nav-chevron" /></RouterLink></nav>
      <div class="sidebar-note"><span class="note-orbit"><LibraryBig :size="21" :stroke-width="1.3" /></span><p>收藏有序，<br />喜欢的都找得到。</p><small>文字目录 · 原文件留在原处</small></div>
      <div v-if="auth.user.value" class="sidebar-bottom">
        <RouterLink to="/account" class="profile-link" :class="{ active: route.path === '/account' }"><span class="avatar">{{ auth.user.value?.username.slice(0, 1).toUpperCase() }}</span><span class="profile-info"><strong>{{ auth.user.value?.username }}</strong><small><ShieldCheck v-if="auth.isAdmin.value" :size="12" /><Eye v-else :size="12" />{{ auth.isAdmin.value ? '管理员' : '看客 · 只读' }}</small></span><UserRound :size="17" /></RouterLink>
        <button class="logout-button" :disabled="loggingOut" @click="logout"><LogOut :size="16" />{{ loggingOut ? '退出中…' : '退出登录' }}<ArrowUpRight :size="14" /></button>
      </div>
    <div v-else class="sidebar-bottom"><RouterLink to="/login" class="profile-link">登录收藏目录</RouterLink></div>
    </aside>
    <div class="main-shell">
      <header class="topbar"><div class="breadcrumb"><button class="icon-button mobile-menu" aria-label="打开导航" @click="mobileOpen = true"><Menu :size="21" /></button><span>资料库</span><ChevronRight :size="14" /><strong>{{ currentTitle }}</strong></div><span class="workspace-status"><span />{{ route.meta.public ? 'PikPak · 在线文件夹' : auth.isAdmin.value ? '私人空间' : '共享资料库 · 只读' }}</span></header>
      <main id="main-content" class="main-content"><RouterView /></main>
      <footer class="site-footer"><span>藏页 · 把喜欢的好好收藏</span><span>PRIVATE COLLECTION</span></footer>
    </div>
  </div>
  <div class="toast-region" aria-live="polite"><TransitionGroup name="toast"><div v-for="toast in toasts" :key="toast.id" class="toast" :class="toast.type"><CheckCircle2 v-if="toast.type === 'success'" :size="19" /><CircleAlert v-else-if="toast.type === 'error'" :size="19" /><Info v-else :size="19" /><span>{{ toast.message }}</span><button type="button" class="icon-button" aria-label="关闭提示" @click="dismissToast(toast.id)"><X :size="15" /></button></div></TransitionGroup></div>
  <Modal :open="confirmation.open" :title="confirmation.title" @close="answerConfirmation(false)"><p class="confirmation-copy">{{ confirmation.description }}</p><template #footer><button class="button secondary" @click="answerConfirmation(false)">取消</button><button class="button" :class="confirmation.danger ? 'danger' : 'primary'" @click="answerConfirmation(true)">{{ confirmation.label }}</button></template></Modal>
</template>
