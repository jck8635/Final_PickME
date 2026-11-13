import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { api, errorMessage } from '../api/client'
import type { Member } from '../api/types'
import { useAuth, useFlash } from '../lib/app-context'

/** 기존 templates/member/login.html */
export default function LoginPage() {
  const { user, setUser } = useAuth()
  const { flash } = useFlash()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // 로그인 후 돌아갈 곳 (같은 사이트 경로만 허용)
  const next = params.get('next')
  const target = next && next.startsWith('/') && !next.startsWith('//') ? next : '/'

  if (user && !submitting) return <Navigate to="/mypage" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    try {
      const res = await api.post<Member>('/auth/login', { email, password })
      setUser(res.data)
      navigate(target, { replace: true })
    } catch (error) {
      flash(errorMessage(error))
      setSubmitting(false)
    }
  }

  return (
    <div className="pm-page pm-auth-layout">
      <aside className="pm-auth-intro">
        <Link className="pm-wordmark" to="/">
          PickMe AI<span className="pm-brand-dot" aria-hidden="true"></span>
        </Link>
        <p className="pm-eyebrow">WELCOME BACK</p>
        <h2>
          다시 만나 반가워요.
          <br />
          오늘의 고민도 함께해요.
        </h2>
        <p>
          복잡한 생각을 차근차근 정리하고,
          <br />
          나에게 맞는 선택에 한 걸음 더 가까이.
        </p>
        <Link className="pm-text-link" to="/">
          ← 홈으로 돌아가기
        </Link>
      </aside>
      <section className="pm-card pm-auth-card" aria-labelledby="login-title">
        <p className="pm-eyebrow">MY PICKME</p>
        <h1 id="login-title">로그인</h1>
        <p className="pm-muted">나만의 고민과 선택을 이어가세요.</p>
        <form onSubmit={submit} className="pm-form" noValidate>
          <label htmlFor="email">이메일</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="username"
            maxLength={255}
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            maxLength={1024}
            placeholder="비밀번호를 입력해주세요"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <button className="pm-button pm-button-full" type="submit" disabled={submitting}>
            {submitting ? '로그인 중...' : '로그인'} <span aria-hidden="true">→</span>
          </button>
        </form>
        <p className="pm-form-footer">
          아직 계정이 없으신가요? <Link to="/signup">회원가입</Link>
        </p>
      </section>
    </div>
  )
}
