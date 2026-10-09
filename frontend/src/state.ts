import { computed, reactive, ref } from 'vue'
import { api, ApiError, refreshCsrf } from './api'
import type { User } from './types'

const user = ref<User | null>(null)
let initialized = false
let pending: Promise<void> | null = null
export const auth = {
  user,
  isAdmin: computed(() => user.value?.role === 'ADMIN'),
  async ensure() {
    if (initialized) return
    if (!pending) pending = (async () => {
      try { user.value = await api<User>('/auth/me'); initialized = true }
      catch (e) { if (e instanceof ApiError && e.status === 401) { user.value = null; initialized = true } else throw e }
    })().finally(() => { pending = null })
    await pending
  },
  async login(username: string, password: string) {
    await refreshCsrf()
    await api('/auth/login', { method: 'POST', form: new URLSearchParams({ username, password }) })
    user.value = await api<User>('/auth/me')
    initialized = true
    await refreshCsrf()
  },
  async logout() {
    await api('/auth/logout', { method: 'POST' })
    this.clear()
    await refreshCsrf()
  },
  clear() { user.value = null; initialized = true },
}
window.addEventListener('session-expired', () => auth.clear())

export interface Toast { id: number; message: string; type: 'success' | 'error' | 'info' }
export const toasts = reactive<Toast[]>([])
let toastId = 0
export function notify(message: string, type: Toast['type'] = 'success') {
  const id = ++toastId
  toasts.push({ id, message, type })
  if (toasts.length > 3) toasts.splice(0, toasts.length - 3)
  window.setTimeout(() => dismissToast(id), type === 'error' ? 7500 : 4000)
}
export function dismissToast(id: number) { const index = toasts.findIndex(t => t.id === id); if (index >= 0) toasts.splice(index, 1) }

export const confirmation = reactive({ open: false, title: '', description: '', label: '确认', danger: false })
let resolveConfirmation: ((result: boolean) => void) | null = null
export function confirmAction(title: string, description: string, label = '确认', danger = false): Promise<boolean> {
  if (resolveConfirmation) resolveConfirmation(false)
  Object.assign(confirmation, { open: true, title, description, label, danger })
  return new Promise(resolve => { resolveConfirmation = resolve })
}
export function answerConfirmation(result: boolean) { confirmation.open = false; resolveConfirmation?.(result); resolveConfirmation = null }
export async function copyText(text: string) {
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text)
    else {
      const input = document.createElement('textarea'); input.value = text; input.style.position = 'fixed'; input.style.opacity = '0'; document.body.append(input); input.select()
      const success = document.execCommand('copy'); input.remove(); if (!success) throw new Error('copy')
    }
    notify('已复制到剪贴板')
  } catch { notify('无法自动复制，请选中内容手动复制。', 'error') }
}
