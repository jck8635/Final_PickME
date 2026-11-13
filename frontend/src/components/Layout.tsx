import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { api, errorMessage } from '../api/client'
import { useAuth, useFlash } from '../lib/app-context'

const navClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'nav-active' : undefined)

/** 기존 templates/base.html */
export default function Layout() {
  const { user, setUser } = useAuth()
  const { messages, flash, dismiss } = useFlash()
  const navigate = useNavigate()
  const location = useLocation()

  // 페이지를 옮기면 맨 위로 (커뮤니티 #post-id 링크는 제외)
  useEffect(() => {
    if (!location.hash) window.scrollTo(0, 0)
  }, [location.pathname, location.hash])

  async function logout() {
    try {
      await api.post('/auth/logout')
      setUser(null)
      flash('로그아웃되었습니다.')
      navigate('/')
    } catch (error) {
      flash(errorMessage(error))
    }
  }

  return (
    <>
      <header>
        <nav>
          <Link className="brand" to="/">
            PickMe AI
          </Link>
          <NavLink to="/chat" className={navClass}>
            AI 상담
          </NavLink>
          <NavLink to="/decision" className={navClass}>
            결정 도우미
          </NavLink>
          <NavLink to="/history" className={navClass}>
            고민 기록
          </NavLink>
          <NavLink to="/community" className={navClass}>
            커뮤니티
          </NavLink>
          {user ? (
            <>
              <NavLink to="/mypage" className={navClass}>
                마이페이지
              </NavLink>
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  void logout()
                }}
              >
                <button type="submit">로그아웃</button>
              </form>
            </>
          ) : (
            <NavLink to="/login" className={navClass}>
              로그인
            </NavLink>
          )}
        </nav>
      </header>
      <main>
        {messages.length > 0 && (
          <div className="alert-stack" role="status" aria-live="polite">
            {messages.map((m) => (
              <div key={m.id} className="alert">
                <span>{m.text}</span>
                <button type="button" className="alert-close" aria-label="알림 닫기" onClick={() => dismiss(m.id)}>
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
        <Outlet />
      </main>
    </>
  )
}
