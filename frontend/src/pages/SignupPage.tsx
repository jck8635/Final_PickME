import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { api, errorMessage } from '../api/client'
import { useAuth, useFlash } from '../lib/app-context'

/** 기존 templates/member/signup.html */
export default function SignupPage() {
  const { user } = useAuth()
  const { flash } = useFlash()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/mypage" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    try {
      const res = await api.post<{ message: string }>('/auth/signup', { email, nickname, password })
      flash(res.data.message)
      navigate('/login')
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
        <p className="pm-eyebrow">YOUR NEXT CHAPTER</p>
        <h2>
          나를 위한 선택,
          <br />
          여기서 시작해요.
        </h2>
        <p>
          AI와 생각을 정리하고, 선택지를 비교하고,
          <br />
          다양한 사람들의 관점을 만나보세요.
        </p>
        <div className="pm-intro-chips">
          <span>AI 상담</span>
          <span>결정 도우미</span>
          <span>고민 기록</span>
          <span>커뮤니티</span>
        </div>
        <Link className="pm-text-link" to="/">
          ← 홈으로 돌아가기
        </Link>
      </aside>
      <section className="pm-card pm-auth-card" aria-labelledby="signup-title">
        <p className="pm-eyebrow">JOIN PICKME</p>
        <h1 id="signup-title">회원가입</h1>
        <p className="pm-muted">세 가지 정보로 나만의 공간을 만들어보세요.</p>
        <form onSubmit={submit} className="pm-form" noValidate>
          <label htmlFor="email">이메일</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
            maxLength={255}
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <label htmlFor="nickname">닉네임</label>
          <input
            id="nickname"
            autoComplete="nickname"
            maxLength={80}
            placeholder="어떻게 불러드릴까요?"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            aria-describedby="nickname-help"
            required
          />
          <p id="nickname-help" className="pm-field-help">
            다른 회원과 겹치지 않는 닉네임 · 최대 80자
          </p>
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            minLength={8}
            maxLength={1024}
            placeholder="8자 이상 입력해주세요"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-describedby="password-help"
            required
          />
          <p id="password-help" className="pm-field-help">
            공백만 사용하지 않고 8자 이상 입력해주세요.
          </p>
          <button className="pm-button pm-button-full" type="submit" disabled={submitting}>
            가입하기 <span aria-hidden="true">→</span>
          </button>
        </form>
        <p className="pm-form-footer">
          이미 계정이 있으신가요? <Link to="/login">로그인</Link>
        </p>
      </section>
    </div>
  )
}
