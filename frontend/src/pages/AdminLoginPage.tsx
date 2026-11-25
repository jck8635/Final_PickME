import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { ShieldCheck, ArrowLeft } from "lucide-react";
import { api, errorMessage } from "../api/client";
import { useAuth } from "../lib/app-context";

export default function AdminLoginPage() {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const [configured, setConfigured] = useState(true);
  const [busy, setBusy] = useState(false);

  const navigate = useNavigate();
  const { refresh } = useAuth();

  useEffect(() => {
    const controller = new AbortController();

    void api
      .get("/admin/auth/me", {
        signal: controller.signal,
      })
      .then((res) => {
        if (res.data.admin) {
          navigate("/admin", { replace: true });
        }

        setConfigured(res.data.configured);
        setReady(true);
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setError(errorMessage(e));
          setReady(true);
        }
      });

    return () => controller.abort();
  }, [navigate]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");

    try {
      await api.post("/admin/auth/login", { username, password });
      await refresh();
      navigate("/admin", { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="adm-login">
      <section className="adm-login-intro">
        <Link to="/" className="adm-logo">
          PickMe<span>관리자</span>
        </Link>

        <div>
          <span className="adm-kicker">COMMUNITY OPERATIONS</span>
          <h1>
            좋은 커뮤니티를
            <br />
            함께 만듭니다.
          </h1>
          <p>
            회원과 게시글을 살피고,
            <br />
            신고된 내용을 확인하는 운영 공간입니다.
          </p>
        </div>

        <Link to="/community">
          <ArrowLeft size={16} /> 커뮤니티로 돌아가기
        </Link>
      </section>

      <section className="adm-login-form">
        <div className="adm-login-box">
          <span className="adm-icon">
            <ShieldCheck size={24} />
          </span>

          <h2>관리자 로그인</h2>
          <p>일반 회원과 별도의 관리자 계정으로 로그인하세요.</p>

          {!ready && <p role="status">연결을 확인하고 있습니다.</p>}

          {ready && !configured && (
            <p className="adm-error">
              관리자 계정이 아직 설정되지 않았습니다. 서버 설정 후 이용해주세요.
            </p>
          )}

          <form onSubmit={submit}>
            <label htmlFor="admin-username">관리자 아이디</label>
            <input
              id="admin-username"
              autoComplete="username"
              value={username}
              maxLength={80}
              required
              onChange={(e) => setUsername(e.target.value)}
            />

            <label htmlFor="admin-password">비밀번호</label>
            <input
              id="admin-password"
              type="password"
              autoComplete="current-password"
              value={password}
              maxLength={1024}
              required
              onChange={(e) => setPassword(e.target.value)}
            />

            {error && (
              <p className="adm-error" role="alert">
                {error}
              </p>
            )}

            <button
              className="adm-primary"
              disabled={!ready || !configured || busy}
              type="submit"
            >
              {busy ? "로그인 중…" : "관리자 로그인"}
            </button>
          </form>

          <small>운영 권한이 있는 관리자만 이용할 수 있습니다.</small>
        </div>
      </section>
    </main>
  );
}
