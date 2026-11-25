import { useEffect } from "react";

import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";

import { api, errorMessage } from "../api/client";
import { useAuth, useFlash } from "../lib/app-context";

const navClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? "nav-active" : undefined;

export default function Layout() {
  const { user, admin, loading, setUser, setAdmin } = useAuth();

  const { messages, flash, dismiss } = useFlash();

  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!location.hash) {
      window.scrollTo(0, 0);
    }
  }, [location.pathname, location.hash]);

  async function logout() {
    const wasAdmin = Boolean(admin);

    try {
      await api.post(wasAdmin ? "/admin/auth/logout" : "/auth/logout");

      setUser(null);
      setAdmin(null);

      flash(wasAdmin ? "관리자 로그아웃되었습니다." : "로그아웃되었습니다.");

      navigate(wasAdmin ? "/admin/login" : "/");
    } catch (error) {
      flash(errorMessage(error));
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

          {!loading &&
            (admin ? (
              <>
                <span
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    padding: "5px 10px",
                    borderRadius: 8,
                    background: "#eaf3ec",
                    color: "#286747",
                    fontSize: 12,
                    fontWeight: 700,
                  }}
                >
                  관리자 · {admin.username}
                </span>

                <Link
                  to="/admin"
                  style={{
                    color: "#286747",
                    fontWeight: 700,
                  }}
                >
                  관리자 페이지
                </Link>

                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    void logout();
                  }}
                >
                  <button type="submit">로그아웃</button>
                </form>
              </>
            ) : user ? (
              <>
                <NavLink to="/mypage" className={navClass}>
                  마이페이지
                </NavLink>

                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    void logout();
                  }}
                >
                  <button type="submit">로그아웃</button>
                </form>
              </>
            ) : (
              <NavLink to="/login" className={navClass}>
                로그인
              </NavLink>
            ))}
        </nav>
      </header>

      <main>
        {messages.length > 0 && (
          <div className="alert-stack" role="status" aria-live="polite">
            {messages.map((message) => (
              <div key={message.id} className="alert">
                <span>{message.text}</span>

                <button
                  type="button"
                  className="alert-close"
                  aria-label="알림 닫기"
                  onClick={() => dismiss(message.id)}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        )}

        {admin && (
          <section
            aria-label="관리자 로그인 안내"
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: 14,
              padding: "16px 20px",
              marginBottom: 24,
              border: "1px solid #d8e7dc",
              borderRadius: 12,
              background: "#f0f7f2",
              color: "#315c40",
            }}
          >
            <div>
              <strong
                style={{
                  display: "block",
                  fontSize: 14,
                }}
              >
                관리자 계정으로 접속 중입니다.
              </strong>

              <p
                style={{
                  margin: "4px 0 0",
                  fontSize: 12,
                  color: "#6d8373",
                }}
              >
                {admin.username}님, 관리자 로그인 상태로 사이트를 둘러보고
                있습니다.
              </p>
            </div>

            <Link
              to="/admin"
              style={{
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                padding: "10px 15px",
                borderRadius: 8,
                background: "#286c51",
                color: "#fff",
                fontSize: 13,
                fontWeight: 600,
                textDecoration: "none",
                whiteSpace: "nowrap",
              }}
            >
              관리자 페이지로 돌아가기 →
            </Link>
          </section>
        )}

        <Outlet />

        <footer className="community-footer">
          <span>PickMe · 함께 고르는 더 나은 선택</span>

          {admin ? (
            <Link to="/admin">관리자 페이지로 돌아가기</Link>
          ) : (
            <Link to="/admin/login">관리자 로그인</Link>
          )}
        </footer>
      </main>
    </>
  );
}
