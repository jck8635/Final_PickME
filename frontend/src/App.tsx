import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Link, Navigate, Route, Routes, useLocation } from 'react-router'
import Layout from './components/Layout'
import { useAuth, useFlash } from './lib/app-context'
import ChatPage from './pages/ChatPage'
import CommunityPage from './pages/CommunityPage'
import DecisionPage from './pages/DecisionPage'
import HistoryPage from './pages/HistoryPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import MyPage from './pages/MyPage'
import SignupPage from './pages/SignupPage'

/** 로그인이 필요한 화면: 로그인 페이지로 보낸 뒤 원래 화면으로 돌아옵니다. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const { flash } = useFlash()
  const location = useLocation()

  useEffect(() => {
    if (!loading && !user) flash('로그인이 필요합니다.')
  }, [loading, user, flash])

  if (loading) return null
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />
  return children
}

function NotFound() {
  return (
    <section className="hero">
      <h1>페이지를 찾을 수 없어요</h1>
      <p>
        <Link to="/">홈으로 돌아가기</Link>
      </p>
    </section>
  )
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="chat" element={<ChatPage />} />
        <Route path="decision" element={<DecisionPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="community" element={<CommunityPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="signup" element={<SignupPage />} />
        <Route
          path="mypage"
          element={
            <RequireAuth>
              <MyPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
