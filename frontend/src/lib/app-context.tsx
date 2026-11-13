import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../api/client'
import type { Member } from '../api/types'

// ---------- 로그인 상태 ----------

type AuthState = {
  user: Member | null
  loading: boolean
  setUser: (user: Member | null) => void
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Member | null>(null)
  const [loading, setLoading] = useState(true)

  // 첫 요청(/auth/me)에서 XSRF-TOKEN 쿠키도 함께 발급됩니다.
  const refresh = useCallback(async () => {
    try {
      const res = await api.get<{ user: Member | null }>('/auth/me')
      setUser(res.data.user)
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const value = useMemo(() => ({ user, loading, setUser, refresh }), [user, loading, refresh])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('AuthProvider 안에서 사용하세요.')
  return ctx
}

// ---------- 알림 메시지 (Flask 의 flash 에 해당) ----------

type Flash = { id: number; text: string }

type FlashState = {
  messages: Flash[]
  flash: (text: string) => void
  dismiss: (id: number) => void
}

const FlashContext = createContext<FlashState | null>(null)

export function FlashProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<Flash[]>([])
  const nextId = useRef(1)

  const dismiss = useCallback((id: number) => {
    setMessages((list) => list.filter((m) => m.id !== id))
  }, [])

  const flash = useCallback(
    (text: string) => {
      const id = nextId.current++
      setMessages((list) => [...list.slice(-2), { id, text }])
      window.setTimeout(() => dismiss(id), 5000)
    },
    [dismiss],
  )

  const value = useMemo(() => ({ messages, flash, dismiss }), [messages, flash, dismiss])
  return <FlashContext.Provider value={value}>{children}</FlashContext.Provider>
}

export function useFlash(): FlashState {
  const ctx = useContext(FlashContext)
  if (!ctx) throw new Error('FlashProvider 안에서 사용하세요.')
  return ctx
}
