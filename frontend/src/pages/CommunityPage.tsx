import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, RefObject } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, errorMessage, isUnauthorized } from '../api/client'
import type { CommentView, CommunityFeed, PostView } from '../api/types'
import { useAuth, useFlash } from '../lib/app-context'
import { formatDate, formatShort } from '../lib/format'

/** 기존 templates/community/index.html + static/js/community.js */

const CATEGORIES = ['음식/식사', '진로/이직', '쇼핑/소비', '연애/관계', '일상/생활', '학습/자기계발', '여행/여가', '기타']
const LOGIN_REQUIRED = '투표, 공감, 댓글, 게시글 작성은 로그인 후 이용할 수 있습니다.'

/** 서버 요청 + 성공 메시지/오류 처리 공통 */
type Run = (action: () => Promise<unknown>, success?: string) => Promise<boolean>

export default function CommunityPage() {
  const { user } = useAuth()
  const { flash } = useFlash()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const category = params.get('category') ?? '전체'
  const q = params.get('q') ?? ''

  const [feed, setFeed] = useState<CommunityFeed | null>(null)
  const [search, setSearch] = useState({ q, category })
  const dialogRef = useRef<HTMLDialogElement>(null)
  const lastUpdate = useRef('')

  const load = useCallback(async () => {
    try {
      const res = await api.get<CommunityFeed>('/community/posts', { params: { category, q } })
      setFeed(res.data)
      lastUpdate.current = res.data.latestUpdate
    } catch (error) {
      flash(errorMessage(error, '게시글을 불러오지 못했어요.'))
    }
  }, [category, q, flash])

  useEffect(() => {
    void load()
  }, [load, user?.id])

  useEffect(() => setSearch({ q, category }), [q, category])

  // 다른 이용자의 투표/댓글이 생기면 5초 안에 화면을 새로 고침
  useEffect(() => {
    let checking = false
    const timer = window.setInterval(async () => {
      if (checking || document.hidden || dialogRef.current?.open) return
      checking = true
      try {
        const res = await api.get<{ updatedAt: string }>('/community/updates')
        if (res.data.updatedAt && lastUpdate.current !== res.data.updatedAt) await load()
      } catch {
        // 새로고침 확인이 실패해도 화면은 계속 사용할 수 있음
      } finally {
        checking = false
      }
    }, 5000)
    return () => window.clearInterval(timer)
  }, [load])

  // #post-<id> 로 들어오면 해당 글로 스크롤
  useEffect(() => {
    if (feed && window.location.hash) document.getElementById(window.location.hash.slice(1))?.scrollIntoView()
  }, [feed])

  const run: Run = useCallback(
    async (action, success) => {
      if (!user) {
        flash(LOGIN_REQUIRED)
        navigate('/login?next=/community')
        return false
      }
      try {
        await action()
        if (success) flash(success)
        await load()
        return true
      } catch (error) {
        if (isUnauthorized(error)) {
          flash(LOGIN_REQUIRED)
          navigate('/login?next=/community')
        } else {
          flash(errorMessage(error))
        }
        return false
      }
    },
    [user, flash, navigate, load],
  )

  function submitSearch(e: FormEvent) {
    e.preventDefault()
    const next = new URLSearchParams()
    if (search.q.trim()) next.set('q', search.q.trim().slice(0, 100))
    if (search.category !== '전체') next.set('category', search.category)
    setParams(next)
  }

  function openNewPost() {
    if (!user) {
      flash(LOGIN_REQUIRED)
      navigate('/login?next=/community')
      return
    }
    dialogRef.current?.showModal()
  }

  const posts = feed?.posts ?? []

  return (
    <>
      <section className="community-page">
        <div className="community-heading">
          <div>
            <p className="eyebrow">집단 지성 &amp; 고민 나눔 광장</p>
            <h1>이용자 투표 커뮤니티</h1>
            <p>다른 이용자의 투표와 관점을 확인하고 함께 결정해보세요.</p>
          </div>
          <button type="button" onClick={openNewPost}>
            ＋ 고민 투표 올리기
          </button>
        </div>

        <form className="community-filter" onSubmit={submitSearch}>
          <input
            type="search"
            value={search.q}
            maxLength={100}
            onChange={(e) => setSearch((s) => ({ ...s, q: e.target.value }))}
            placeholder="제목, 내용, 선택지 검색"
          />
          <select value={search.category} onChange={(e) => setSearch((s) => ({ ...s, category: e.target.value }))}>
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
            {feed && posts.length === 0 && (
              <div className="community-empty">조건에 맞는 고민이 없습니다. 첫 번째 투표를 올려보세요!</div>
            )}
            {feed?.featuredPostId && (
              <p className="featured-note">
                {category} 인기 고민을 먼저 보여드리고, 나머지는 최신순으로 정렬했습니다.
              </p>
            )}

            <div className="community-feed">
              {posts.map((post) => (
                <PostCard
                  key={post.id}
                  post={post}
                  featured={post.id === feed?.featuredPostId}
                  loggedIn={Boolean(user)}
                  run={run}
                />
              ))}
            </div>
          </div>

          <aside className="community-sidebar" aria-label="인기 게시물">
            <RankPanel title="공감 많은 고민" unit="공감" items={feed?.topLiked ?? []} />
            <RankPanel title="댓글 많은 고민" unit="댓글" items={feed?.topCommented ?? []} />
          </aside>
        </div>
      </section>

      <NewPostDialog dialogRef={dialogRef} run={run} />
    </>
  )
}

function RankPanel(props: { title: string; unit: string; items: { id: number; title: string; count: number }[] }) {
  return (
    <section className="popular-panel">
      <h2>{props.title}</h2>
      <ol>
        {props.items.length === 0 && <li className="popular-empty">아직 게시물이 없습니다.</li>}
        {props.items.map((item) => (
          <li key={item.id}>
            <a
              href={`#post-${item.id}`}
              onClick={(e) => {
                const el = document.getElementById(`post-${item.id}`)
                if (el) {
                  e.preventDefault()
                  el.scrollIntoView({ behavior: 'smooth' })
                  history.replaceState(null, '', `#post-${item.id}`)
                }
              }}
            >
              {item.title}
            </a>
            <span>
              {item.count} {props.unit}
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}

function PostCard({ post, featured, loggedIn, run }: { post: PostView; featured: boolean; loggedIn: boolean; run: Run }) {
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)

  async function act(action: () => Promise<unknown>, success?: string) {
    setBusy(true)
    const ok = await run(action, success)
    setBusy(false)
    return ok
  }

  async function submitComment(e: FormEvent) {
    e.preventDefault()
    const ok = await act(() => api.post(`/community/posts/${post.id}/comments`, { content: comment }), '댓글을 등록했습니다.')
    if (ok) setComment('')
  }

  return (
    <article className="community-card" id={`post-${post.id}`}>
      <div className="post-meta">
        {featured && <span className="featured-pill">인기 글</span>}
        <span className="category-pill">{post.category}</span>
        <span>작성자 {post.authorNickname}</span>
        <time dateTime={post.createdAt}>{formatDate(post.createdAt)}</time>
        {post.aiRecommendation && <span className="ai-pill">AI 추천: {post.aiRecommendation}</span>}
      </div>

      {post.mine && (
        <div
          className="post-owner-actions"
          style={{ display: 'flex', justifyContent: 'flex-end', gap: 6, alignItems: 'center', margin: '4px 0', fontSize: '0.8em' }}
        >
          <EditPostForm post={post} run={run} />
          <span style={{ color: '#ccc', fontSize: 10 }}>|</span>
          <button
            type="button"
            className="text-button danger-button"
            style={{ fontSize: 'inherit' }}
            disabled={busy}
            onClick={() => {
              if (window.confirm('게시글과 투표, 공감, 댓글이 모두 삭제됩니다. 삭제할까요?')) {
                void act(() => api.delete(`/community/posts/${post.id}`), '게시글을 삭제했습니다.')
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
          const voted = post.myOptionId === option.id
          return (
            <div key={option.id} className="option-form">
              <button
                className={`vote-option${voted ? ' is-voted' : ''}`}
                type="button"
                disabled={busy}
                onClick={() =>
                  void act(() => api.post(`/community/posts/${post.id}/votes/${option.id}`), '투표가 반영되었습니다.')
                }
              >
                <span className="vote-bar" style={{ width: `${option.percent}%` }}></span>
                <span className="option-name">
                  {option.name}
                  {voted && ' ✓'}
                </span>
                <span className="vote-count">
                  {option.count}표 · {option.percent}%
                </span>
              </button>
            </div>
          )
        })}
      </div>

      {loggedIn && post.myOptionId && (
        <div className="cancel-vote-form">
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={() => void act(() => api.delete(`/community/posts/${post.id}/votes`), '투표를 취소했습니다.')}
          >
            투표 취소
          </button>
        </div>
      )}

      <div className="post-actions">
        <button
          className={`text-button${post.liked ? ' liked' : ''}`}
          type="button"
          disabled={busy}
          onClick={() => void act(() => api.post(`/community/posts/${post.id}/like`))}
        >
          ♥ 공감 {post.likeCount}
        </button>
        <span>댓글 및 조언 {post.comments.length}개</span>
      </div>

      <section className="comment-list">
        {post.comments.map((c) => (
          <CommentItem key={c.id} postId={post.id} comment={c} run={run} />
        ))}

        {loggedIn ? (
          <form className="comment-form" onSubmit={submitComment}>
            <input
              value={comment}
              maxLength={2000}
              required
              onChange={(e) => setComment(e.target.value)}
              placeholder="경험이나 조언을 댓글로 나눠주세요"
            />
            <button type="submit" disabled={busy}>
              등록
            </button>
          </form>
        ) : (
          <p className="login-hint">
            <Link to="/login?next=/community">로그인</Link>하면 댓글을 남길 수 있습니다.
          </p>
        )}
      </section>
    </article>
  )
}

function EditPostForm({ post, run }: { post: PostView; run: Run }) {
  const detailsRef = useRef<HTMLDetailsElement>(null)
  const [form, setForm] = useState({ title: post.title, content: post.content, category: post.category })

  async function submit(e: FormEvent) {
    e.preventDefault()
    const ok = await run(() => api.put(`/community/posts/${post.id}`, form), '게시글을 수정했습니다.')
    if (ok && detailsRef.current) detailsRef.current.open = false
  }

  return (
    <details className="inline-edit" ref={detailsRef}>
      <summary className="text-button" style={{ cursor: 'pointer', fontSize: 'inherit' }}>
        수정
      </summary>
      <form className="owner-edit-form" style={{ marginTop: 8, fontSize: '1rem' }} onSubmit={submit}>
        <label>
          카테고리
          <select value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))} required>
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
            type="text"
            maxLength={200}
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
            required
          />
        </label>
        <label>
          내용
          <textarea
            maxLength={5000}
            rows={3}
            value={form.content}
            onChange={(e) => setForm((f) => ({ ...f, content: e.target.value }))}
            required
          />
        </label>
        <button type="submit">수정 저장</button>
      </form>
    </details>
  )
}

function CommentItem({ postId, comment, run }: { postId: number; comment: CommentView; run: Run }) {
  const detailsRef = useRef<HTMLDetailsElement>(null)
  const [content, setContent] = useState(comment.content)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const ok = await run(
      () => api.put(`/community/posts/${postId}/comments/${comment.id}`, { content }),
      '댓글을 수정했습니다.',
    )
    if (ok && detailsRef.current) detailsRef.current.open = false
  }

  return (
    <div className="comment">
      <div className="comment-meta">
        <strong>{comment.authorNickname}</strong>
        <time dateTime={comment.createdAt}>{formatShort(comment.createdAt)}</time>
      </div>

      {comment.mine && (
        <div
          className="comment-owner-actions"
          style={{ display: 'flex', justifyContent: 'flex-end', gap: 6, alignItems: 'center', margin: '2px 0', fontSize: '0.8em' }}
        >
          <details className="inline-edit comment-owner-edit" ref={detailsRef}>
            <summary className="text-button" style={{ cursor: 'pointer', fontSize: 'inherit' }}>
              수정
            </summary>
            <form className="owner-edit-form" style={{ marginTop: 6, fontSize: '1rem' }} onSubmit={submit}>
              <textarea maxLength={2000} rows={2} value={content} onChange={(e) => setContent(e.target.value)} required />
              <button type="submit">댓글 수정 저장</button>
            </form>
          </details>
          <span style={{ color: '#ccc', fontSize: 10 }}>|</span>
          <button
            type="button"
            className="text-button danger-button"
            style={{ fontSize: 'inherit' }}
            onClick={() => {
              if (window.confirm('이 댓글을 삭제할까요?')) {
                void run(() => api.delete(`/community/posts/${postId}/comments/${comment.id}`), '댓글을 삭제했습니다.')
              }
            }}
          >
            삭제
          </button>
        </div>
      )}

      <p className="comment-content">{comment.content}</p>
    </div>
  )
}

function NewPostDialog({ dialogRef, run }: { dialogRef: RefObject<HTMLDialogElement | null>; run: Run }) {
  const empty = { category: CATEGORIES[0], title: '', content: '', options: ['', '', '', '', ''] }
  const [form, setForm] = useState(empty)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const ok = await run(
      () =>
        api.post('/community/posts', {
          ...form,
          options: form.options.map((o) => o.trim()).filter(Boolean),
        }),
      '고민 투표를 등록했습니다.',
    )
    if (ok) {
      setForm(empty)
      dialogRef.current?.close()
    }
  }

  return (
    <dialog ref={dialogRef} className="post-dialog">
      <form onSubmit={submit}>
        <h2>새로운 고민 투표 등록</h2>
        <label>
          카테고리
          <select value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))} required>
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
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
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
            onChange={(e) => setForm((f) => ({ ...f, content: e.target.value }))}
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
              setForm((f) => ({ ...f, options: f.options.map((o, j) => (j === i ? e.target.value : o)) }))
            }
            placeholder={i < 2 ? `선택지 ${i + 1}` : `선택지 ${i + 1} (선택)`}
          />
        ))}
        <div className="dialog-actions">
          <button type="button" className="secondary" onClick={() => dialogRef.current?.close()}>
            취소
          </button>
          <button type="submit">투표 올리기</button>
        </div>
      </form>
    </dialog>
  )
}
