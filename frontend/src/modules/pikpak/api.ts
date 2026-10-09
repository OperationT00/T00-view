export interface Share { originalUrl: string | null; id: string; link: { shareId: string }; name: string; note: string; version: number }
export interface Entry { fileId: string; parentId: string; name: string; kind: 'FOLDER' | 'VIDEO' | 'OTHER'; sizeBytes: number | null }
export interface Page { entries: Entry[]; nextCursor: string }
export interface Media { id: string; label: string; uri: string; protocol: 'PROGRESSIVE' | 'HLS' | 'MPEG_TS'; mimeType: string; videoCodec: string | null; durationSeconds: number | null }
export interface Playback { shareId: string; fileId: string; variants: Media[] }
let csrf: { token: string; headerName: string } | null = null
let loading: Promise<void> | null = null
export async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' })
  if (!['GET', 'HEAD'].includes(method)) {
    if (!csrf) {
      if (!loading) loading = fetch('/api/auth/csrf', { cache: 'no-store' }).then(async r => {
        if (!r.ok) throw new Error('无法准备请求，请刷新后重试')
        csrf = await r.json()
      }).finally(() => { loading = null })
      await loading
    }
    if (!csrf) throw new Error('无法准备请求，请刷新后重试')
    headers.set(csrf.headerName, csrf.token)
    if (body !== undefined) headers.set('Content-Type', 'application/json')
  }
  const response = await fetch('/api/pikpak' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store' })
  if (!response.ok) {
    if (response.status === 403) csrf = null
    const data = await response.json().catch(() => ({}))
    throw new Error(data.message || (response.status === 404 ? 'PikPak 模块未启用或记录已移除' : '请求失败，请稍后重试'))
  }
  const text = await response.text()
  return text ? JSON.parse(text) : undefined as T
}
export const message = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试'
export function size(value: number | null) { if (value === null) return '大小未知'; return value > 1024 ** 3 ? (value / 1024 ** 3).toFixed(1) + ' GB' : (value / 1024 ** 2).toFixed(1) + ' MB' }

export const shareUrl = (s: Share) => s.originalUrl || `https://mypikpak.com/s/${encodeURIComponent(s.link.shareId)}`
export const exportUrl = (id?: string) => '/api/pikpak/shares/export.txt' + (id ? `?id=${encodeURIComponent(id)}` : '')
