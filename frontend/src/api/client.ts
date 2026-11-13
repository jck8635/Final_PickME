import axios, { AxiosError } from 'axios'

// 모든 요청은 /api 로 시작 -> vite proxy -> Spring Boot
// Spring Security 가 내려준 XSRF-TOKEN 쿠키를 axios 가 X-XSRF-TOKEN 헤더로 자동 첨부합니다.
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
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
