import { isAxiosError } from "axios";
import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router";

import ReportButton from "../components/ReportButton";
import { api, errorMessage } from "../api/client";
import type { CommentView, PostView } from "../api/types";
import { useAuth } from "../lib/app-context";
import { formatDate, formatShort } from "../lib/format";
import { CATEGORIES, useCommunityAction } from "./community-actions";
import type { Run } from "./community-actions";

export default function CommunityDetailPage() {
  const { postId } = useParams();

  return <CommunityDetail key={postId} postId={postId ?? ""} />;
}

function CommunityDetail({ postId }: { postId: string }) {
  const { user } = useAuth();
  const { search } = useLocation();

  const backUrl = `/community${search}`;

  const [post, setPost] = useState<PostView | null>(null);
  const [error, setError] = useState("");
  const [reporting, setReporting] = useState(false);

  const request = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    request.current?.abort();

    const controller = new AbortController();
    request.current = controller;

    try {
      const res = await api.get<PostView>(`/community/posts/${postId}`, {
        signal: controller.signal,
      });

      if (!controller.signal.aborted) {
        setPost(res.data);
        setError("");
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        if (isAxiosError(error) && error.response?.status === 404) {
          setPost(null);
        }

        setError(errorMessage(error, "게시글을 불러오지 못했어요."));
      }
    }
  }, [postId]);

  useEffect(() => {
    void load();

    return () => request.current?.abort();
  }, [load, user?.id]);

  useEffect(() => {
    const controller = new AbortController();

    void api
      .get<{ available: boolean }>("/community/reporting", {
        signal: controller.signal,
      })
      .then(({ data }) => {
        if (!controller.signal.aborted) {
          setReporting(data.available);
        }
      })
      .catch(() => {});

    return () => controller.abort();
  }, []);

  const run = useCommunityAction(load);

  return (
    <section className="community-page">
      <p>
        <Link to={backUrl}>← 게시글 목록</Link>
      </p>

      {error && (
        <p role="alert">
          {error}{" "}
          <button type="button" onClick={() => void load()}>
            다시 불러오기
          </button>
        </p>
      )}

      {!post && !error && <p role="status">게시글을 불러오는 중입니다.</p>}

      {post && (
        <PostCard
          post={post}
          loggedIn={Boolean(user)}
          reporting={reporting}
          run={run}
          backUrl={backUrl}
        />
      )}
    </section>
  );
}

function PostCard({
  post,
  loggedIn,
  reporting,
  run,
  backUrl,
}: {
  post: PostView;
  backUrl: string;
  loggedIn: boolean;
  reporting: boolean;
  run: Run;
}) {
  const navigate = useNavigate();
  const { pathname, search } = useLocation();

  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);

  const [commentChecking, setCommentChecking] = useState(false);
  const [commentError, setCommentError] = useState("");
  const [commentNotice, setCommentNotice] = useState("");

  const commentPending = useRef(false);

  async function act(
    action: Parameters<Run>[0],
    success?: string,
    options?: Parameters<Run>[2],
  ) {
    setBusy(true);

    try {
      return await run(action, success, options);
    } finally {
      setBusy(false);
    }
  }

  async function submitComment(e: FormEvent) {
    e.preventDefault();

    if (commentPending.current || busy) return;

    setCommentError("");
    setCommentNotice("");

    const content = comment.trim();

    if (!content || content.length > 2000) {
      setCommentError("댓글은 1~2,000자로 작성해주세요.");
      return;
    }

    commentPending.current = true;
    setCommentChecking(true);

    let warning = "";

    try {
      const ok = await act(
        async () => {
          // 서버에서 게시글과 동일한 유해성 검사를 거친 뒤 저장합니다.
          const response = await api.post<{ warning?: string }>(
            `/community/posts/${post.id}/comments`,
            { content },
          );

          warning =
            typeof response.data.warning === "string"
              ? response.data.warning
              : "";

          return response;
        },
        "댓글을 등록했습니다.",
        {
          onError: setCommentError,
        },
      );

      if (ok) {
        setComment("");
        setCommentNotice(warning || "댓글을 등록했습니다.");
      }
    } finally {
      commentPending.current = false;
      setCommentChecking(false);
    }
  }

  return (
    <article className="community-card" id={`post-${post.id}`}>
      <div className="post-meta">
        <span className="category-pill">{post.category}</span>
        <span>작성자 {post.authorNickname}</span>

        <time dateTime={post.createdAt}>{formatDate(post.createdAt)}</time>

        {post.aiRecommendation && (
          <span className="ai-pill">AI 추천: {post.aiRecommendation}</span>
        )}
      </div>

      <ReportButton postId={post.id} enabled={reporting} />

      {post.mine && (
        <div className="post-owner-actions">
          <EditPostForm post={post} run={run} />

          <button
            type="button"
            className="text-button danger-button"
            disabled={busy}
            onClick={async () => {
              if (
                !window.confirm(
                  "게시글과 투표, 공감, 댓글이 모두 삭제됩니다. 삭제할까요?",
                )
              ) {
                return;
              }

              setBusy(true);

              try {
                const ok = await run(
                  () => api.delete(`/community/posts/${post.id}`),
                  "게시글을 삭제했습니다.",
                  {
                    refresh: false,
                  },
                );

                if (ok) {
                  navigate(backUrl, { replace: true });
                }
              } finally {
                setBusy(false);
              }
            }}
          >
            삭제
          </button>
        </div>
      )}

      <h2>{post.title}</h2>
      <p className="post-content">{post.content}</p>

      <div className="vote-heading">
        <strong>선택지를 클릭해 투표하세요</strong>
        <span>총 {post.totalVotes}명 참여</span>
      </div>

      <div className="option-list">
        {post.options.map((option) => {
          const voted = post.myOptionId === option.id;

          return (
            <div key={option.id} className="option-form">
              <button
                className={`vote-option${voted ? " is-voted" : ""}`}
                type="button"
                disabled={busy}
                onClick={() =>
                  void act(
                    () =>
                      api.post(
                        `/community/posts/${post.id}/votes/${option.id}`,
                      ),
                    "투표가 반영되었습니다.",
                  )
                }
              >
                <span
                  className="vote-bar"
                  style={{ width: `${option.percent}%` }}
                />

                <span className="option-name">
                  {option.name}
                  {voted && " ✓"}
                </span>

                <span className="vote-count">
                  {option.count}표 · {option.percent}%
                </span>
              </button>
            </div>
          );
        })}
      </div>

      {loggedIn && post.myOptionId && (
        <div className="cancel-vote-form">
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={() =>
              void act(
                () => api.delete(`/community/posts/${post.id}/votes`),
                "투표를 취소했습니다.",
              )
            }
          >
            투표 취소
          </button>
        </div>
      )}

      <div className="post-actions">
        <button
          className={`text-button${post.liked ? " liked" : ""}`}
          type="button"
          disabled={busy}
          onClick={() =>
            void act(() => api.post(`/community/posts/${post.id}/like`))
          }
        >
          ♥ 공감 {post.likeCount}
        </button>

        <span>댓글 및 조언 {post.commentCount}개</span>
      </div>

      <section className="comment-list">
        {post.comments.map((commentItem) => (
          <CommentItem
            key={commentItem.id}
            postId={post.id}
            comment={commentItem}
            run={run}
          />
        ))}

        {loggedIn ? (
          <>
            <form
              className="comment-form"
              onSubmit={submitComment}
              aria-busy={commentChecking}
            >
              <input
                value={comment}
                maxLength={2000}
                required
                disabled={busy}
                aria-label="댓글 내용"
                aria-invalid={Boolean(commentError)}
                aria-describedby={`comment-feedback-${post.id}`}
                onChange={(e) => {
                  setComment(e.target.value);
                  setCommentError("");
                  setCommentNotice("");
                }}
                placeholder="경험이나 조언을 댓글로 나눠주세요"
              />

              <button type="submit" disabled={busy || !comment.trim()}>
                {commentChecking ? "검사 중..." : "등록"}
              </button>
            </form>

            <div id={`comment-feedback-${post.id}`}>
              {commentChecking && (
                <p role="status">댓글 내용을 확인하고 있습니다.</p>
              )}

              {commentError && (
                <p role="alert" className="text-sm text-red-700">
                  {commentError}
                </p>
              )}

              {commentNotice && (
                <p role="status" className="text-sm text-slate-700">
                  {commentNotice}
                </p>
              )}
            </div>
          </>
        ) : (
          <p className="login-hint">
            <Link to={`/login?next=${encodeURIComponent(pathname + search)}`}>
              로그인
            </Link>
            하면 댓글을 남길 수 있습니다.
          </p>
        )}
      </section>
    </article>
  );
}

function EditPostForm({ post, run }: { post: PostView; run: Run }) {
  const detailsRef = useRef<HTMLDetailsElement>(null);

  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    title: post.title,
    content: post.content,
    category: post.category,
  });

  async function submit(e: FormEvent) {
    e.preventDefault();

    if (busy) return;

    setBusy(true);

    try {
      const ok = await run(
        () => api.put(`/community/posts/${post.id}`, form),
        "게시글을 수정했습니다.",
      );

      if (ok && detailsRef.current) {
        detailsRef.current.open = false;
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <details className="inline-edit" ref={detailsRef}>
      <summary className="text-button">수정</summary>

      <form className="owner-edit-form" onSubmit={submit}>
        <label>
          카테고리
          <select
            value={form.category}
            onChange={(e) =>
              setForm((current) => ({
                ...current,
                category: e.target.value,
              }))
            }
            required
          >
            {CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </select>
        </label>

        <label>
          제목
          <input
            maxLength={200}
            value={form.title}
            onChange={(e) =>
              setForm((current) => ({
                ...current,
                title: e.target.value,
              }))
            }
            required
          />
        </label>

        <label>
          내용
          <textarea
            maxLength={5000}
            rows={3}
            value={form.content}
            onChange={(e) =>
              setForm((current) => ({
                ...current,
                content: e.target.value,
              }))
            }
            required
          />
        </label>

        <button type="submit" disabled={busy}>
          수정 저장
        </button>
      </form>
    </details>
  );
}

function CommentItem({
  postId,
  comment,
  run,
}: {
  postId: number;
  comment: CommentView;
  run: Run;
}) {
  const detailsRef = useRef<HTMLDetailsElement>(null);
  const pending = useRef(false);

  const [busy, setBusy] = useState(false);
  const [content, setContent] = useState(comment.content);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function submit(e: FormEvent) {
    e.preventDefault();

    if (pending.current) return;

    setError("");
    setNotice("");

    const value = content.trim();

    if (!value || value.length > 2000) {
      setError("댓글은 1~2,000자로 작성해주세요.");
      return;
    }

    pending.current = true;
    setBusy(true);

    let warning = "";

    try {
      const ok = await run(
        async () => {
          // 댓글 수정도 서버의 동일한 유해성 검사를 통과해야 합니다.
          const response = await api.put<{ warning?: string }>(
            `/community/posts/${postId}/comments/${comment.id}`,
            {
              content: value,
            },
          );

          warning =
            typeof response.data.warning === "string"
              ? response.data.warning
              : "";

          return response;
        },
        "댓글을 수정했습니다.",
        {
          onError: setError,
        },
      );

      if (ok) {
        setContent(value);
        setNotice(warning || "댓글을 수정했습니다.");

        if (detailsRef.current) {
          detailsRef.current.open = false;
        }
      }
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <div className="comment">
      <div className="comment-meta">
        <strong>{comment.authorNickname}</strong>

        <time dateTime={comment.createdAt}>
          {formatShort(comment.createdAt)}
        </time>
      </div>

      {comment.mine && (
        <div className="comment-owner-actions">
          <details className="inline-edit comment-owner-edit" ref={detailsRef}>
            <summary className="text-button">수정</summary>

            <form
              className="owner-edit-form"
              onSubmit={submit}
              aria-busy={busy}
            >
              <textarea
                maxLength={2000}
                rows={2}
                value={content}
                onChange={(e) => {
                  setContent(e.target.value);
                  setError("");
                  setNotice("");
                }}
                aria-label="수정할 댓글 내용"
                aria-invalid={Boolean(error)}
                aria-describedby={`edit-comment-feedback-${comment.id}`}
                disabled={busy}
                required
              />

              <button type="submit" disabled={busy || !content.trim()}>
                {busy ? "검사 중..." : "댓글 수정 저장"}
              </button>

              <div id={`edit-comment-feedback-${comment.id}`}>
                {busy && (
                  <p role="status">수정한 댓글 내용을 확인하고 있습니다.</p>
                )}

                {error && (
                  <p role="alert" className="text-sm text-red-700">
                    {error}
                  </p>
                )}
              </div>
            </form>
          </details>

          <button
            type="button"
            className="text-button danger-button"
            disabled={busy}
            onClick={() => {
              if (window.confirm("이 댓글을 삭제할까요?")) {
                void run(
                  () =>
                    api.delete(
                      `/community/posts/${postId}/comments/${comment.id}`,
                    ),
                  "댓글을 삭제했습니다.",
                );
              }
            }}
          >
            삭제
          </button>
        </div>
      )}

      <p className="comment-content">{comment.content}</p>

      {notice && (
        <p role="status" className="text-sm text-slate-700">
          {notice}
        </p>
      )}
    </div>
  );
}
