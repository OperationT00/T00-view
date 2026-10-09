export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}
let csrf: { token: string; headerName: string } | null = null
let csrfPromise: Promise<void> | null = null

export async function refreshCsrf(): Promise<void> {
  csrf = null
  if (!csrfPromise) csrfPromise = (async () => {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    if (!response.ok) throw new ApiError(response.status, 'CSRF_UNAVAILABLE', '无法建立安全连接，请稍后重试。')
    csrf = await response.json()
  })().finally(() => { csrfPromise = null })
  return csrfPromise
}
export async function api<T = void>(path: string, options: { method?: string; body?: unknown; form?: URLSearchParams; query?: Record<string, unknown> } = {}): Promise<T> {
  const method = options.method || 'GET'
  const url = new URL(`/api${path}`, window.location.origin)
  for (const [key, value] of Object.entries(options.query || {})) {
    if (value !== '' && value !== undefined && value !== null) url.searchParams.set(key, String(value))
  }
  const headers = new Headers({ Accept: 'application/json' })
  if (method !== 'GET' && method !== 'HEAD') {
    if (!csrf) await refreshCsrf()
    if (!csrf) throw new Error('无法获取安全令牌，请刷新页面。')
    headers.set(csrf.headerName, csrf.token)
  }
  let body: BodyInit | undefined
  if (options.form) { headers.set('Content-Type', 'application/x-www-form-urlencoded'); body = options.form }
  else if (options.body !== undefined) { headers.set('Content-Type', 'application/json'); body = JSON.stringify(options.body) }
  let response: Response
  try { response = await fetch(url, { method, headers, body, credentials: 'same-origin', cache: 'no-store' }) }
  catch { throw new Error('连接失败，请检查网络或服务是否正在运行。') }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    if (response.status === 401 && path !== '/auth/login' && path !== '/auth/me') window.dispatchEvent(new Event('session-expired'))
    throw new ApiError(response.status, data.code || `HTTP_${response.status}`, data.message || ({ 401: '登录已过期，请重新登录。', 403: '没有操作权限，或安全令牌已过期，请刷新后重试。', 409: '记录已发生变化，请刷新后重试。' }[response.status] || '操作未完成，请稍后重试。'))
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T
  const content = await response.text()
  return content ? JSON.parse(content) as T : undefined as T
}
export function errorMessage(error: unknown): string { return error instanceof Error ? error.message : '操作未完成，请稍后重试。' }
