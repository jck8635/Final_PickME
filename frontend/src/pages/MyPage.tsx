import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { api, errorMessage } from '../api/client'
import type { Member } from '../api/types'
import { useAuth, useFlash } from '../lib/app-context'

/** 기존 templates/member/mypage.html (RequireAuth 안에서만 렌더링) */
export default function MyPage() {
  const { user, setUser } = useAuth()
  const { flash } = useFlash()
  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
  const [busy, setBusy] = useState(false)

  if (!user) return null

  async function saveNickname(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      const res = await api.patch<Member>('/members/me/nickname', { nickname })
      setUser(res.data)
      setNickname(res.data.nickname)
      flash('닉네임을 변경했습니다.')
    } catch (error) {
      flash(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  async function savePassword(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.patch('/members/me/password', { currentPassword, newPassword, passwordConfirmation })
      setCurrentPassword('')
      setNewPassword('')
      setPasswordConfirmation('')
      flash('비밀번호를 변경했습니다.')
    } catch (error) {
      flash(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="pm-page pm-mypage">
      <div className="pm-section-heading">
        <div>
          <p className="pm-eyebrow">MY PICKME</p>
          <h1>마이페이지</h1>
        </div>
        <Link className="pm-text-link" to="/">
          홈으로 →
        </Link>
      </div>
      <section className="pm-profile" aria-labelledby="profile-title">
        <div className="pm-avatar" aria-hidden="true">
          {user.nickname.slice(0, 1)}
        </div>
        <div className="pm-profile-info">
          <h2 id="profile-title">{user.nickname}님, 반가워요.</h2>
          <p>{user.email}</p>
        </div>
        <Link className="pm-button pm-button-secondary" to="/history">
          내 고민 기록 보기 <span aria-hidden="true">↗</span>
        </Link>
      </section>
      <div className="pm-settings-grid">
        <section className="pm-card" aria-labelledby="nickname-title">
          <span className="pm-step">01 / PROFILE</span>
          <h2 id="nickname-title">내 정보</h2>
          <p className="pm-muted">PickMe AI에서 사용할 이름을 정해주세요.</p>
          <dl className="pm-account-info">
            <dt>이메일</dt>
            <dd>{user.email}</dd>
            <dt>현재 닉네임</dt>
            <dd>{user.nickname}</dd>
          </dl>
          <form onSubmit={saveNickname} className="pm-form" noValidate>
            <label htmlFor="nickname">변경할 닉네임</label>
            <input
              id="nickname"
              autoComplete="nickname"
              maxLength={80}
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              aria-describedby="nickname-help"
              required
            />
            <p id="nickname-help" className="pm-field-help">
              중복되지 않는 닉네임 · 최대 80자
            </p>
            <button className="pm-button" type="submit" disabled={busy}>
              닉네임 저장
            </button>
          </form>
        </section>
        <section className="pm-card" aria-labelledby="password-title">
          <span className="pm-step">02 / SECURITY</span>
          <h2 id="password-title">비밀번호 변경</h2>
          <p className="pm-muted">현재 비밀번호를 확인한 후 변경할 수 있어요.</p>
          <form onSubmit={savePassword} className="pm-form" noValidate>
            <label htmlFor="current-password">현재 비밀번호</label>
            <input
              id="current-password"
              type="password"
              autoComplete="current-password"
              maxLength={1024}
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
            <label htmlFor="new-password">새 비밀번호</label>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              minLength={8}
              maxLength={1024}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              aria-describedby="password-help"
              required
            />
            <p id="password-help" className="pm-field-help">
              현재와 다른 비밀번호 · 공백만 사용하지 않고 8자 이상
            </p>
            <label htmlFor="password-confirmation">새 비밀번호 확인</label>
            <input
              id="password-confirmation"
              type="password"
              autoComplete="new-password"
              minLength={8}
              maxLength={1024}
              value={passwordConfirmation}
              onChange={(e) => setPasswordConfirmation(e.target.value)}
              required
            />
            <button className="pm-button" type="submit" disabled={busy}>
              비밀번호 변경
            </button>
          </form>
        </section>
      </div>
    </div>
  )
}
