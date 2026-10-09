import { createRouter, createWebHashHistory } from 'vue-router'
import { auth } from './state'
import { errorMessage } from './api'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/shares', name: 'pikpak-shares', component: () => import('./modules/pikpak/PikPakShares.vue'), meta: { title: 'pikpak', public: true } },
    { path: '/shares/duplicates', name: 'pikpak-duplicates', component: () => import('./modules/pikpak/PikPakDuplicates.vue'), meta: { title: '疑似重复视频', public: true } },
    { path: '/shares/:id', name: 'pikpak-folder', component: () => import('./modules/pikpak/PikPakFolder.vue'), meta: { title: '分享内容', public: true } },
    { path: '/shares/:id/watch/:fileId', name: 'pikpak-watch', redirect: to => ({ name: 'pikpak-folder', params: { id: to.params.id }, query: { folder: to.query.parent, path: to.query.path } }), meta: { public: true } },
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/', redirect: '/assets' },
    { path: '/assets', name: 'assets', component: () => import('./views/AssetsView.vue'), meta: { title: '我的收藏' } },
    { path: '/assets/:id', name: 'asset', component: () => import('./views/AssetDetailView.vue'), meta: { title: '收藏详情' } },
    { path: '/tags', name: 'tags', component: () => import('./views/TagsView.vue'), meta: { title: '标签整理' } },
    { path: '/sources', name: 'sources', component: () => import('./views/SourcesView.vue'), meta: { title: '存储来源' } },
    { path: '/parsing', name: 'parsing', component: () => import('./views/ParsingView.vue'), meta: { title: '来源解析', admin: true } },
    { path: '/users', name: 'users', component: () => import('./views/UsersView.vue'), meta: { title: '账号管理', admin: true } },
    { path: '/account', name: 'account', component: () => import('./views/AccountView.vue'), meta: { title: '我的账号' } },
    { path: '/:pathMatch(.*)*', redirect: '/assets' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async to => {
  if (to.meta.public) return
  try { await auth.ensure() }
  catch (e) { if (to.name !== 'login') return { name: 'login', query: { error: errorMessage(e) } }; return }
  if (!auth.user.value && to.name !== 'login') return { name: 'login', query: { redirect: to.fullPath } }
  if (auth.user.value && to.name === 'login') return { name: 'assets' }
  if (to.meta.admin && !auth.isAdmin.value) return { name: 'assets' }
})
router.afterEach(to => { document.title = `${to.meta.title || '私人收藏目录'} · 藏页` })
export default router
