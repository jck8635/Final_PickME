import axios, { AxiosError } from 'axios'

// 모든 요청은 /api 로 시작 -> vite proxy -> Spring Boot
// Spring Security 가 내려준 XSRF-TOKEN 쿠키를 axios 가 X-XSRF-TOKEN 헤더로 자동 첨부합니다.
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

// Access token stays in memory; refresh cookie is never read by JavaScript.
let accessToken: string | null = null
let pending: Promise<boolean> | null = null
let generation = 0
const raw = axios.create({ baseURL: '/api', withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN', xsrfHeaderName: 'X-XSRF-TOKEN' })

function sessionId(token: string | null): string | null {
  if (!token) return null
  try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sid ?? null }
  catch { return null }
}

function clearToken() {
  generation++
  accessToken = null
  window.dispatchEvent(new Event('pickme-auth-cleared'))
}

export function restoreToken(): Promise<boolean> {
  if (pending) return pending
  const epoch = generation
  const previousSid = sessionId(accessToken)
  const run = async () => {
    try {
      await raw.get('/auth/me') // bootstrap CSRF cookie
      const res = await raw.post<{ accessToken: string }>('/auth/refresh')
      if (epoch !== generation) return false
      if (previousSid && previousSid !== sessionId(res.data.accessToken)) { clearToken(); return false }
      accessToken = res.data.accessToken
      return true
    } catch (error) {
      if (epoch === generation && error instanceof AxiosError && error.response?.status === 401) clearToken()
      return false
    }
  }
  // Serialize refresh across tabs when Web Locks is supported.
  pending = (navigator.locks ? navigator.locks.request('pickme-refresh', run) : run())
    .finally(() => { pending = null })
  return pending
}

export async function ensureToken() {
  return accessToken !== null || restoreToken()
}

const isLogin = (url?: string) => url === '/auth/login' || url === '/admin/auth/login'
const isLogout = (url?: string) => url === '/auth/logout' || url === '/admin/auth/logout'
api.interceptors.request.use(async config => {
  if (isLogin(config.url)) {
    if (pending) await pending
    await raw.get('/auth/me')
  } else if (accessToken) config.headers.set('Authorization', `Bearer ${accessToken}`)
  return config
})
api.interceptors.response.use(res => {
  if (isLogin(res.config.url)) {
    generation++
    accessToken = res.data.accessToken
  } else if (isLogout(res.config.url) || res.config.url === '/members/me/password') clearToken()
  return res
}, async (error: AxiosError) => {
  const config = error.config as (NonNullable<AxiosError['config']> & { _retried?: boolean }) | undefined
  if (error.response?.status !== 401 || !config || config._retried || isLogin(config.url) || !config.headers.get('Authorization')) throw error
  if (!accessToken) throw error
  config._retried = true
  const sent = config.headers.get('Authorization')
  if (accessToken && sent !== `Bearer ${accessToken}`) {
    if (typeof sent !== 'string' || sessionId(sent.slice(7)) !== sessionId(accessToken)) throw error
    config.headers.set('Authorization', `Bearer ${accessToken}`)
    return api.request(config)
  }
  if (await restoreToken() && accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
    return api.request(config)
  }
  throw error
})

/** 서버의 {"message": "..."} 또는 네트워크 오류를 화면용 문장으로 바꿉니다. */
export function errorMessage(error: unknown, fallback = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.'): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message) return data.message
    if (error.code === 'ECONNABORTED') return '응답 시간이 초과됐어요. 잠시 후 다시 시도해 주세요.'
    if (!error.response) return '서버와 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'
  }
  return fallback
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof AxiosError && error.response?.status === 401
}
