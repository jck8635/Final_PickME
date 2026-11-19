import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent, RefObject } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router";

import { api, errorMessage } from "../api/client";
import type { CommunityFeed } from "../api/types";
import { useAuth, useFlash } from "../lib/app-context";
import { formatDate } from "../lib/format";
import {
  CATEGORIES,
  LOGIN_REQUIRED,
  useCommunityAction,
} from "./community-actions";
import type { Run } from "./community-actions";

export default function CommunityPage() {
  const { user } = useAuth();
  const { flash } = useFlash();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  const category = params.get("category") ?? "전체";
  const q = params.get("q") ?? "";
  const scope = params.get("scope") ?? "all";
  const requestedPage = Number(params.get("page") ?? "1");
  const page =
    Number.isSafeInteger(requestedPage) &&
    requestedPage > 0 &&
    requestedPage <= 107374182
      ? requestedPage
      : 1;
  const searchString = params.toString() ? `?${params.toString()}` : "";
  const legacyPost = useLocation().hash.match(/^#post-([0-9]+)$/)?.[1];

  const [feed, setFeed] = useState<CommunityFeed | null>(null);
  const [loadError, setLoadError] = useState("");
  const [search, setSearch] = useState({ q, category });

  const dialogRef = useRef<HTMLDialogElement>(null);
  const lastUpdate = useRef("");
  const requestVersion = useRef(0);

  const load = useCallback(async () => {
    const version = ++requestVersion.current;

    try {
      const res = await api.get<CommunityFeed>("/community/posts", {
        params: { category, q, scope, page },
      });

      if (version !== requestVersion.current) return;

      setFeed(res.data);
      setLoadError("");
      lastUpdate.current = res.data.latestUpdate;
    } catch (error) {
      if (version === requestVersion.current) {
        setLoadError(errorMessage(error, "게시글을 불러오지 못했어요."));
      }
    }
  }, [category, q, scope, page]);

  useEffect(() => {
    requestVersion.current++;
    setFeed(null);
    setLoadError("");

    if (scope !== "all" && !user) return;

    void load();

    return () => {
      requestVersion.current++;
    };
  }, [load, user?.id, scope]);

  useEffect(() => {
    setSearch({ q, category });
  }, [q, category]);

  useEffect(() => {
    let checking = false;
    let active = true;

    const timer = window.setInterval(async () => {
      if (
        checking ||
        document.hidden ||
        document.querySelector("dialog[open]") ||
        (scope !== "all" && !user)
      ) {
        return;
      }

      checking = true;

      try {
        const res = await api.get<{ updatedAt: string }>("/community/updates");

        if (active && lastUpdate.current !== res.data.updatedAt) {
          await load();
        }
      } catch {
        // 현재 화면 유지
      } finally {
        checking = false;
      }
    }, 5000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [load, scope, user]);

  const run = useCommunityAction(load);

  useEffect(() => {
    if (legacyPost) {
      navigate(
        { pathname: `/community/posts/${legacyPost}`, search: searchString },
        { replace: true },
      );
    }
  }, [legacyPost, navigate, searchString]);

  useEffect(() => {
    if (feed && feed.page > Math.max(1, feed.totalPages)) {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          next.set("page", String(Math.max(1, feed.totalPages)));
          return next;
        },
        { replace: true },
      );
    }
  }, [feed, setParams]);

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(params);
    next.set("page", String(nextPage));
    setParams(next);
  }

  function submitSearch(e: FormEvent) {
    e.preventDefault();

    const next = new URLSearchParams();

    if (scope !== "all") next.set("scope", scope);
    if (search.q.trim()) {
      next.set("q", search.q.trim().slice(0, 100));
    }
    if (search.category !== "전체") {
      next.set("category", search.category);
    }

    setParams(next);
  }

  function openNewPost() {
    if (!user) {
      flash(LOGIN_REQUIRED);
      navigate("/login?next=/community");
      return;
    }

    dialogRef.current?.showModal();
  }

  const posts = feed?.posts ?? [];

  return (
    <>
      <section className="community-page">
        <div className="community-heading">
          <div>
            <p className="eyebrow">COMMUNITY · 나누는 고민, 넓어지는 선택</p>
            <h1>함께 고르는 커뮤니티</h1>
            <p>다른 이용자의 투표와 관점을 확인하고 함께 결정해보세요.</p>
          </div>

          <button type="button" onClick={openNewPost}>
            ＋ 고민 투표 올리기
          </button>
        </div>

        <div className="community-tabs" aria-label="게시글 분류">
          {(
            [
              ["all", "전체 글"],
              ["mine", "내 게시글"],
              ["commented", "댓글 작성한 글"],
              ["liked", "공감한 글"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={scope === value}
              onClick={() => {
                if (value !== "all" && !user) {
                  flash("로그인이 필요합니다.");
                  navigate(
                    `/login?next=${encodeURIComponent(
                      "/community?scope=" + value,
                    )}`,
                  );
                  return;
                }

                const next = new URLSearchParams(params);
                next.set("scope", value);
                next.delete("page");
                setParams(next);
              }}
            >
              {label}
            </button>
          ))}
        </div>

        {scope !== "all" && !user && (
          <p>
            <Link
              to={`/login?next=${encodeURIComponent(
                "/community?scope=" + scope,
              )}`}
            >
              로그인 후 내 활동을 확인하세요.
            </Link>
          </p>
        )}

        <form className="community-filter" onSubmit={submitSearch}>
          <input
            type="search"
            value={search.q}
            maxLength={100}
            onChange={(e) =>
              setSearch((s) => ({
                ...s,
                q: e.target.value,
              }))
            }
            placeholder="제목, 내용, 선택지 검색"
          />

          <select
            value={search.category}
            onChange={(e) =>
              setSearch((s) => ({
                ...s,
                category: e.target.value,
              }))
            }
          >
            <option value="전체">전체 카테고리</option>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>

          <button type="submit">검색</button>
        </form>

        <div className="community-layout">
          <div className="community-feed-column">
            {loadError && (
              <p role="alert">
                {loadError}{" "}
                <button type="button" onClick={() => void load()}>
                  다시 불러오기
                </button>
              </p>
            )}
            {!feed && !loadError && (scope === "all" || user) && (
              <p role="status">게시글을 불러오는 중입니다.</p>
            )}

            {feed && posts.length === 0 && (
              <div className="community-empty">
                {scope === "mine"
                  ? "아직 작성한 게시글이 없습니다."
                  : scope === "commented"
                    ? "댓글을 작성한 게시글이 없습니다."
                    : scope === "liked"
                      ? "공감한 게시글이 없습니다."
                      : "조건에 맞는 게시글이 없습니다."}
              </div>
            )}

            {feed?.featuredPost && (
              <p className="featured-note">
                <span className="featured-pill">카테고리 인기 글</span>{" "}
                <Link
                  to={{
                    pathname: `/community/posts/${feed.featuredPost.id}`,
                    search: searchString,
                  }}
                >
                  {feed.featuredPost.title}
                </Link>
              </p>
            )}

            <div className="community-feed">
              {posts.map((post) => (
                <article className="community-card" key={post.id}>
                  <div className="post-meta">
                    <span className="category-pill">{post.category}</span>
                    <span>작성자 {post.authorNickname}</span>
                    <time dateTime={post.createdAt}>
                      {formatDate(post.createdAt)}
                    </time>
                  </div>
                  <h2>
                    <Link
                      to={{
                        pathname: `/community/posts/${post.id}`,
                        search: searchString,
                      }}
                    >
                      {post.title}
                    </Link>
                  </h2>
                  <p className="post-content">
                    {post.content.length > 180
                      ? `${post.content.slice(0, 180)}…`
                      : post.content}
                  </p>
                  <div className="post-actions">
                    <span>투표 {post.totalVotes}표</span>
                    <span>공감 {post.likeCount}개</span>
                    <span>댓글 {post.commentCount}개</span>
                  </div>
                </article>
              ))}
            </div>

            {feed && feed.totalPages > 1 && feed.page <= feed.totalPages && (
              <nav className="dialog-actions" aria-label="게시글 페이지">
                <button
                  type="button"
                  disabled={page <= 1}
                  onClick={() => goToPage(page - 1)}
                >
                  이전
                </button>
                <span aria-live="polite">
                  {page} / {feed.totalPages}
                </span>
                <button
                  type="button"
                  disabled={page >= feed.totalPages}
                  onClick={() => goToPage(page + 1)}
                >
                  다음
                </button>
              </nav>
            )}
          </div>

          <aside className="community-sidebar" aria-label="인기 게시물">
            <RankPanel
              title="공감 많은 고민"
              unit="공감"
              search={searchString}
              items={feed?.topLiked ?? []}
            />
            <RankPanel
              title="댓글 많은 고민"
              unit="댓글"
              search={searchString}
              items={feed?.topCommented ?? []}
            />
          </aside>
        </div>
      </section>

      <NewPostDialog dialogRef={dialogRef} run={run} />
    </>
  );
}

function RankPanel({
  title,
  unit,
  items,
  search,
}: {
  title: string;
  unit: string;
  search: string;
  items: { id: number; title: string; count: number }[];
}) {
  return (
    <section className="popular-panel">
      <h2>{title}</h2>
      <ol>
        {items.length === 0 && (
          <li className="popular-empty">아직 게시물이 없습니다.</li>
        )}

        {items.map((item) => (
          <li key={item.id}>
            <Link to={{ pathname: `/community/posts/${item.id}`, search }}>
              {item.title}
            </Link>
            <span>
              {item.count} {unit}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}

function NewPostDialog({
  dialogRef,
  run,
}: {
  dialogRef: RefObject<HTMLDialogElement | null>;
  run: Run;
}) {
  const empty = {
    category: CATEGORIES[0],
    title: "",
    content: "",
    options: ["", "", "", "", ""],
  };

  const [form, setForm] = useState(empty);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();

    if (busy) return;
    setError("");
    setBusy(true);
    try {
      const ok = await run(
        () =>
          api.post("/community/posts", {
            ...form,
            options: form.options.map((o) => o.trim()).filter(Boolean),
          }),
        "고민 투표를 등록했습니다.",
        { onError: setError },
      );

      if (ok) {
        setForm(empty);
        dialogRef.current?.close();
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="post-dialog"
      onClose={() => setError("")}
    >
      <form onSubmit={submit}>
        <h2>새로운 고민 투표 등록</h2>

        <label>
          카테고리
          <select
            value={form.category}
            onChange={(e) =>
              setForm((f) => ({
                ...f,
                category: e.target.value,
              }))
            }
            required
          >
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>

        <label>
          제목
          <input
            maxLength={200}
            required
            value={form.title}
            onChange={(e) =>
              setForm((f) => ({
                ...f,
                title: e.target.value,
              }))
            }
            placeholder="고민을 한 줄로 표현해보세요"
          />
        </label>

        <label>
          고민 내용
          <textarea
            maxLength={5000}
            rows={4}
            required
            value={form.content}
            onChange={(e) =>
              setForm((f) => ({
                ...f,
                content: e.target.value,
              }))
            }
            placeholder="상황과 배경을 적어주세요"
          />
        </label>

        <label>선택지 (최소 2개, 최대 5개)</label>

        {form.options.map((value, i) => (
          <input
            key={i}
            name="options"
            maxLength={120}
            required={i < 2}
            value={value}
            onChange={(e) =>
              setForm((f) => ({
                ...f,
                options: f.options.map((option, j) =>
                  j === i ? e.target.value : option,
                ),
              }))
            }
            placeholder={i < 2 ? `선택지 ${i + 1}` : `선택지 ${i + 1} (선택)`}
          />
        ))}

        {error && <p role="alert">{error}</p>}

        <div className="dialog-actions">
          <button
            type="button"
            className="secondary"
            onClick={() => dialogRef.current?.close()}
          >
            취소
          </button>
          <button type="submit" disabled={busy}>
            투표 올리기
          </button>
        </div>
      </form>
    </dialog>
  );
}
