import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import {
  Users,
  FileText,
  Flag,
  LayoutDashboard,
  LogOut,
  Search,
  ArrowUpRight,
  Trash2,
  Eye,
  X,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";

import { api, errorMessage, isUnauthorized } from "../api/client";
import { useAuth } from "../lib/app-context";
import { formatDate } from "../lib/format";

type Tab = "overview" | "members" | "posts" | "reports";
type Counts = { members: number; posts: number; pending: number };
type Member = { id: number; nickname: string; email: string; posts: number };
type Post = {
  id: number;
  title: string;
  category: string;
  author: string;
  createdAt: string;
};
type Report = {
  id: number;
  postId: number;
  title: string;
  reporter: string;
  reason: string;
  status: string;
  createdAt: string;
};
type Page<T> = { items: T[]; total: number; page: number; size: number };
type Detail = Post & { content: string; options: string[] };
type Removal = { type: "members" | "posts"; id: number; name: string };

const tabs = [
  { key: "overview", label: "운영 현황", icon: LayoutDashboard },
  { key: "members", label: "회원 관리", icon: Users },
  { key: "posts", label: "게시글 관리", icon: FileText },
  { key: "reports", label: "신고 관리", icon: Flag },
] as const;

const empty = { items: [], total: 0, page: 0, size: 10 };

export default function AdminPage() {
  const [username, setUsername] = useState("");
  const [tab, setTab] = useState<Tab>("overview");
  const [counts, setCounts] = useState<Counts>({
    members: 0,
    posts: 0,
    pending: 0,
  });
  const [members, setMembers] = useState<Page<Member>>(empty);
  const [posts, setPosts] = useState<Page<Post>>(empty);
  const [reports, setReports] = useState<Page<Report>>(empty);
  const [q, setQ] = useState("");
  const [draft, setDraft] = useState("");
  const [status, setStatus] = useState("PENDING");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [removal, setRemoval] = useState<Removal | null>(null);
  const [confirmation, setConfirmation] = useState("");

  const detailDialog = useRef<HTMLDialogElement>(null);
  const deleteDialog = useRef<HTMLDialogElement>(null);
  const navigate = useNavigate();
  const { refresh } = useAuth();

  const fail = useCallback(
    (e: unknown) => {
      if (isUnauthorized(e)) {
        navigate("/admin/login", { replace: true });
      } else {
        setError(errorMessage(e));
      }
    },
    [navigate],
  );

  useEffect(() => {
    const controller = new AbortController();

    void api
      .get("/admin/auth/me", {
        signal: controller.signal,
      })
      .then((res) => {
        if (!res.data.admin) {
          navigate("/admin/login", { replace: true });
        } else {
          setUsername(res.data.admin.username);
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) fail(e);
      });

    return () => controller.abort();
  }, [navigate, fail]);

  useEffect(() => {
    if (!username) return;

    const controller = new AbortController();
    setLoading(true);
    setError("");

    const jobs = [
      api
        .get<Counts>("/admin/overview", {
          signal: controller.signal,
        })
        .then((r) => setCounts(r.data)),
    ];

    if (tab !== "overview") {
      jobs.push(
        api
          .get(`/admin/${tab}`, {
            params: { q, page, status },
            signal: controller.signal,
          })
          .then((r) => {
            if (page > 0 && r.data.items.length === 0) {
              setPage(page - 1);
              return;
            }

            if (tab === "members") setMembers(r.data);
            if (tab === "posts") setPosts(r.data);
            if (tab === "reports") setReports(r.data);
          }),
      );
    }

    void Promise.all(jobs)
      .catch((e) => {
        if (!controller.signal.aborted) fail(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [username, tab, q, page, status, revision, fail]);

  useEffect(() => {
    if (detail) detailDialog.current?.showModal();
  }, [detail]);

  useEffect(() => {
    if (removal) deleteDialog.current?.showModal();
  }, [removal]);

  function select(value: Tab) {
    setTab(value);
    setPage(0);
    setQ("");
    setDraft("");
    setError("");
    setNotice("");
  }

  async function openPost(id: number) {
    setBusy(true);

    try {
      setDetail((await api.get<Detail>(`/admin/posts/${id}`)).data);
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  async function reject(id: number) {
    setBusy(true);

    try {
      await api.patch(`/admin/reports/${id}/reject`);
      setNotice("신고를 기각했습니다.");
      setRevision((r) => r + 1);
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  function askRemove(target: Removal) {
    setConfirmation("");
    setRemoval(target);
  }

  async function remove() {
    if (!removal || confirmation !== "삭제") return;

    setBusy(true);

    try {
      await api.delete(`/admin/${removal.type}/${removal.id}`);

      setNotice(
        removal.type === "members"
          ? "회원을 탈퇴 처리했습니다."
          : "게시글을 삭제했습니다.",
      );

      deleteDialog.current?.close();
      setRemoval(null);
      setConfirmation("");
      setRevision((r) => r + 1);
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);

    try {
      await api.post("/admin/auth/logout");
      await refresh();
      navigate("/admin/login", { replace: true });
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  const title = tabs.find((t) => t.key === tab)?.label;
  const result =
    tab === "members" ? members : tab === "posts" ? posts : reports;

  if (!username) {
    return (
      <main className="adm-loading">
        {error || "관리자 권한을 확인하고 있습니다."}
      </main>
    );
  }

  return (
    <div className="adm-shell">
      <aside className="adm-sidebar">
        <Link
          to="/admin"
          className="adm-logo"
          onClick={() => select("overview")}
        >
          PickMe<span>관리자</span>
        </Link>

        <p className="adm-side-label">WORKSPACE</p>

        <nav aria-label="관리자 메뉴">
          {tabs.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              type="button"
              aria-current={tab === key ? "page" : undefined}
              onClick={() => select(key)}
            >
              <Icon size={19} />
              {label}
              {key === "reports" && counts.pending > 0 && (
                <span className="adm-count">{counts.pending}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="adm-side-bottom">
          <Link to="/community">
            커뮤니티 보기 <ArrowUpRight size={16} />
          </Link>
          <button type="button" disabled={busy} onClick={() => void logout()}>
            <LogOut size={17} /> 로그아웃
          </button>
        </div>
      </aside>

      <main className="adm-main">
        <header className="adm-topbar">
          <span>
            관리자 공간 <span className="adm-separator">/</span> {title}
          </span>
          <span className="adm-account">
            <span>{username.slice(0, 1).toUpperCase()}</span>
            {username}
          </span>
        </header>

        <div className="adm-content">
          <div className="adm-page-heading">
            <div>
              <p className="adm-kicker">PICKME ADMIN</p>
              <h1>{title}</h1>
              <p>
                {tab === "overview"
                  ? "커뮤니티의 현재 상태를 한눈에 살펴보세요."
                  : tab === "members"
                    ? "가입 회원을 조회하고 필요한 계정을 관리하세요."
                    : tab === "posts"
                      ? "모든 게시글을 살펴보고 운영 기준에 따라 관리하세요."
                      : "신고 사유와 게시글을 확인하고 처리해주세요."}
              </p>
            </div>

            <button
              className="adm-secondary"
              type="button"
              disabled={loading || busy}
              onClick={() => setRevision((r) => r + 1)}
            >
              새로고침
            </button>
          </div>

          {notice && (
            <div className="adm-notice" role="status">
              {notice}
              <button aria-label="알림 닫기" onClick={() => setNotice("")}>
                <X size={16} />
              </button>
            </div>
          )}

          {error && (
            <p className="adm-error" role="alert">
              {error}
            </p>
          )}

          <div className="adm-stats">
            {[
              {
                label: "전체 회원",
                value: counts.members,
                icon: Users,
                target: "members",
              },
              {
                label: "전체 게시글",
                value: counts.posts,
                icon: FileText,
                target: "posts",
              },
              {
                label: "처리 대기 신고",
                value: counts.pending,
                icon: Flag,
                target: "reports",
              },
            ].map(({ label, value, icon: Icon, target }) => (
              <button
                key={target}
                className="adm-stat"
                type="button"
                onClick={() => select(target as Tab)}
              >
                <span className={`adm-stat-icon ${target}`}>
                  <Icon size={21} />
                </span>
                <span>
                  <span>{label}</span>
                  <strong>
                    {value.toLocaleString()}
                    <small>건</small>
                  </strong>
                </span>
                <ArrowUpRight size={17} />
              </button>
            ))}
          </div>

          {tab === "overview" ? (
            <section className="adm-panel adm-welcome">
              <span className="adm-kicker">TODAY'S CHECK</span>
              <h2>
                {counts.pending
                  ? `확인할 신고가 ${counts.pending}건 있습니다.`
                  : "접수된 대기 신고가 없습니다."}
              </h2>
              <p>
                신고된 글의 내용과 사유를 함께 확인해주세요. 신고와 관계없이
                게시글 관리에서 모든 글을 조회할 수 있습니다.
              </p>
              <button className="adm-primary" onClick={() => select("reports")}>
                신고 내역 확인 <ArrowUpRight size={16} />
              </button>
            </section>
          ) : (
            <section className="adm-panel">
              <div className="adm-panel-heading">
                <h2>
                  {title} <span>{result.total.toLocaleString()}</span>
                </h2>
                <span>페이지당 10건</span>
              </div>

              <div className="adm-toolbar">
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    setQ(draft.trim());
                    setPage(0);
                  }}
                >
                  <Search size={18} />
                  <input
                    aria-label="관리 목록 검색"
                    placeholder={
                      tab === "members"
                        ? "이메일 또는 닉네임 검색"
                        : tab === "posts"
                          ? "제목 또는 작성자 검색"
                          : "게시글 제목 또는 신고 사유 검색"
                    }
                    value={draft}
                    maxLength={100}
                    onChange={(e) => setDraft(e.target.value)}
                  />
                  <button type="submit">검색</button>
                </form>

                {tab === "reports" && (
                  <select
                    aria-label="신고 상태"
                    value={status}
                    onChange={(e) => {
                      setStatus(e.target.value);
                      setPage(0);
                    }}
                  >
                    <option value="PENDING">처리 대기</option>
                    <option value="REJECTED">기각</option>
                    <option value="ALL">전체 신고</option>
                  </select>
                )}
              </div>

              {loading ? (
                <div className="adm-empty" role="status">
                  목록을 불러오고 있습니다.
                </div>
              ) : error ? (
                <div className="adm-empty">
                  목록을 불러오지 못했습니다. 새로고침으로 다시 시도해주세요.
                </div>
              ) : result.items.length === 0 ? (
                <div className="adm-empty">
                  {q ? "검색 결과가 없습니다." : "표시할 내역이 없습니다."}
                </div>
              ) : (
                <div className="adm-table-wrap">
                  <table className="adm-table">
                    <thead>
                      <tr>
                        {(tab === "members"
                          ? ["회원", "이메일", "작성 글", "관리"]
                          : tab === "posts"
                            ? ["게시글", "작성자", "작성일", "관리"]
                            : ["신고 게시글 · 사유", "신고자", "상태", "관리"]
                        ).map((t) => (
                          <th key={t} scope="col">
                            {t}
                          </th>
                        ))}
                      </tr>
                    </thead>

                    <tbody>
                      {tab === "members" &&
                        members.items.map((m) => (
                          <tr key={m.id}>
                            <td>
                              <strong>{m.nickname}</strong>
                              <small>회원 #{m.id}</small>
                            </td>
                            <td>{m.email}</td>
                            <td>{m.posts}개</td>
                            <td>
                              <button
                                className="adm-danger-link"
                                disabled={busy}
                                onClick={() =>
                                  askRemove({
                                    type: "members",
                                    id: m.id,
                                    name: m.nickname,
                                  })
                                }
                              >
                                <Trash2 size={15} /> 탈퇴 처리
                              </button>
                            </td>
                          </tr>
                        ))}

                      {tab === "posts" &&
                        posts.items.map((p) => (
                          <tr key={p.id}>
                            <td>
                              <strong>{p.title}</strong>
                              <small>
                                #{p.id} · {p.category}
                              </small>
                            </td>
                            <td>{p.author}</td>
                            <td>{formatDate(p.createdAt)}</td>
                            <td>
                              <div className="adm-row-actions">
                                <button
                                  disabled={busy}
                                  onClick={() => void openPost(p.id)}
                                >
                                  <Eye size={15} /> 내용
                                </button>
                                <button
                                  className="adm-danger-link"
                                  disabled={busy}
                                  onClick={() =>
                                    askRemove({
                                      type: "posts",
                                      id: p.id,
                                      name: p.title,
                                    })
                                  }
                                >
                                  <Trash2 size={15} /> 삭제
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}

                      {tab === "reports" &&
                        reports.items.map((r) => (
                          <tr key={r.id}>
                            <td>
                              <strong>{r.title}</strong>
                              <p className="adm-reason">{r.reason}</p>
                              <small>
                                신고 #{r.id} · {formatDate(r.createdAt)}
                              </small>
                            </td>
                            <td>{r.reporter}</td>
                            <td>
                              <span
                                className={`adm-badge ${r.status === "PENDING" ? "pending" : ""}`}
                              >
                                {r.status === "PENDING" ? "처리 대기" : "기각"}
                              </span>
                            </td>
                            <td>
                              <div className="adm-row-actions">
                                <button
                                  disabled={busy}
                                  onClick={() => void openPost(r.postId)}
                                >
                                  <Eye size={15} /> 내용
                                </button>
                                {r.status === "PENDING" && (
                                  <button
                                    disabled={busy}
                                    onClick={() => void reject(r.id)}
                                  >
                                    기각
                                  </button>
                                )}
                                <button
                                  className="adm-danger-link"
                                  disabled={busy}
                                  onClick={() =>
                                    askRemove({
                                      type: "posts",
                                      id: r.postId,
                                      name: r.title,
                                    })
                                  }
                                >
                                  <Trash2 size={15} /> 글 삭제
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                    </tbody>
                  </table>
                </div>
              )}

              <div className="adm-pagination">
                <span>
                  {page + 1} / {Math.max(1, Math.ceil(result.total / 10))}{" "}
                  페이지
                </span>
                <div>
                  <button
                    aria-label="이전 페이지"
                    disabled={loading || page === 0}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    <ChevronLeft size={17} />
                  </button>
                  <button
                    aria-label="다음 페이지"
                    disabled={loading || (page + 1) * 10 >= result.total}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    <ChevronRight size={17} />
                  </button>
                </div>
              </div>
            </section>
          )}
        </div>
      </main>

      <dialog
        ref={detailDialog}
        className="adm-dialog"
        onClose={() => setDetail(null)}
      >
        <div className="adm-dialog-heading">
          <h2>게시글 내용</h2>
          <button
            aria-label="게시글 내용 닫기"
            onClick={() => detailDialog.current?.close()}
          >
            <X size={20} />
          </button>
        </div>
        {detail && (
          <>
            <p className="adm-muted">
              #{detail.id} · {detail.category} · {detail.author}
            </p>
            <h3>{detail.title}</h3>
            <p className="adm-body-text">{detail.content}</p>
            <ol>
              {detail.options.map((option, i) => (
                <li key={i}>{option}</li>
              ))}
            </ol>
            <Link to={`/community#post-${detail.id}`}>커뮤니티에서 보기 ↗</Link>
          </>
        )}
      </dialog>

      <dialog
        ref={deleteDialog}
        className="adm-dialog"
        onClose={() => {
          setRemoval(null);
          setConfirmation("");
        }}
      >
        <div className="adm-dialog-heading">
          <h2>
            {removal?.type === "members" ? "회원 탈퇴 처리" : "게시글 삭제"}
          </h2>
          <button
            disabled={busy}
            aria-label="삭제 취소"
            onClick={() => deleteDialog.current?.close()}
          >
            <X size={20} />
          </button>
        </div>

        <p>
          <strong>{removal?.name}</strong>
        </p>
        <p>
          {removal?.type === "members"
            ? "회원과 해당 회원의 게시글·댓글·투표·공감·신고·고민 기록이 삭제됩니다."
            : "게시글과 관련 선택지·투표·공감·댓글·신고가 삭제됩니다."}{" "}
          복구할 수 없습니다.
        </p>

        <label htmlFor="delete-confirm">
          계속하려면 ‘삭제’를 입력해주세요.
        </label>
        <input
          id="delete-confirm"
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
          autoComplete="off"
        />

        <div className="adm-dialog-actions">
          <button
            className="adm-secondary"
            disabled={busy}
            onClick={() => deleteDialog.current?.close()}
          >
            취소
          </button>
          <button
            className="adm-danger"
            disabled={busy || confirmation !== "삭제"}
            onClick={() => void remove()}
          >
            {busy ? "처리 중…" : "삭제"}
          </button>
        </div>
      </dialog>
    </div>
  );
}
