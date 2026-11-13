import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { api, errorMessage } from '../api/client'
import type { ChatResponse, DecisionDraft, Tone } from '../api/types'

/** 기존 templates/chat/index.html (스크립트 포함)을 React 로 옮긴 것 */

const MAX_LEN = 1000
const REQUEST_TIMEOUT_MS = 60000
const TONE_STORAGE_KEY = 'pickme_tone'
const TONES: { value: Tone; label: string }[] = [
  { value: 'empathy', label: '💬 부드러운 공감형' },
  { value: 'logic', label: '📊 객관적인 분석형' },
  { value: 'neutral', label: '⚖️ 차분한 중립형' },
]
const QUICK = [
  { label: '🍜 짬뽕 vs 짜장면', message: '오늘 점심으로 짬뽕과 짜장면 중 무엇을 먹을지 고민돼요.' },
  { label: '💼 이직할지 고민돼요', message: '이직을 할지 현재 회사에 남을지 고민돼요.' },
  { label: '🌙 오늘 약속 갈까 말까', message: '오늘 약속에 나갈지 집에서 쉴지 고민돼요.' },
]
const WELCOME =
  '안녕하세요! 저는 PickMe AI예요. 😊\n\n' +
  '어떤 선택 앞에서 고민하고 계신가요? 상황과 선택지를 편하게 말씀해 주세요. ' +
  '두 가지 대안을 비교하고 싶다면 아래 빠른 추천을 눌러 시작할 수도 있어요.'

type Message = {
  id: number
  sender: 'user' | 'ai'
  text: string
  isError?: boolean
  isCrisis?: boolean
  retryText?: string
  time?: string
}

function escapeHtml(value: string) {
  return value.replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string,
  )
}

/** 원본 formatText: HTML 이스케이프 후 **굵게** 와 상담 전화번호 링크만 허용 */
function formatText(value: string) {
  return escapeHtml(value)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\b(109|129|1577-0199|112|119)\b/g, '<a href="tel:$1" class="chat-phone-link">$1</a>')
}

function nowTime() {
  return new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

function loadTone(): Tone {
  try {
    const saved = localStorage.getItem(TONE_STORAGE_KEY)
    if (TONES.some((t) => t.value === saved)) return saved as Tone
  } catch {
    // 저장소를 사용할 수 없으면 기본값 사용
  }
  return 'empathy'
}

function decisionLink(draft: DecisionDraft) {
  const params = new URLSearchParams({
    title: draft.title || '고민 요약',
    category: draft.category || '기타',
    options: JSON.stringify(draft.options.slice(0, 2)),
  })
  if (draft.content) params.set('content', draft.content)
  return `/decision?${params.toString()}`
}

export default function ChatPage() {
  const nextId = useRef(1)
  const [messages, setMessages] = useState<Message[]>(() => [{ id: 0, sender: 'ai', text: WELCOME }])
  const [input, setInput] = useState('')
  const [tone, setTone] = useState<Tone>(loadTone)
  const [sending, setSending] = useState(false)
  const [draft, setDraft] = useState<DecisionDraft | null>(null)
  const [showScrollBtn, setShowScrollBtn] = useState(false)

  const containerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const stickRef = useRef(true)

  const isNearBottom = () => {
    const el = containerRef.current
    return !el || el.scrollHeight - el.scrollTop - el.clientHeight < 100
  }

  const scrollToBottom = useCallback(() => {
    const el = containerRef.current
    if (el) el.scrollTop = el.scrollHeight
    setShowScrollBtn(false)
  }, [])

  // 새 메시지가 붙으면 아래로 (사용자가 위로 올려 읽는 중이면 유지)
  useLayoutEffect(() => {
    if (stickRef.current) scrollToBottom()
  }, [messages, sending, scrollToBottom])

  // 입력창 자동 높이
  useLayoutEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`
  }, [input])

  useEffect(() => {
    try {
      localStorage.setItem(TONE_STORAGE_KEY, tone)
    } catch {
      // 저장 실패는 상담 기능에 영향을 주지 않음
    }
  }, [tone])

  function append(msg: Omit<Message, 'id'>) {
    stickRef.current = msg.sender === 'user' || isNearBottom()
    setMessages((list) => [...list, { ...msg, id: nextId.current++ }])
  }

  async function sendMessage(raw: string, { skipUserBubble = false } = {}) {
    const message = raw.trim()
    if (!message || sending) return
    if (message.length > MAX_LEN) {
      append({ sender: 'ai', text: `입력은 ${MAX_LEN}자 이내로 작성해 주세요.`, isError: true, time: nowTime() })
      return
    }
    if (!skipUserBubble) append({ sender: 'user', text: message, time: nowTime() })
    setInput('')
    setSending(true)
    stickRef.current = true

    try {
      const res = await api.post<ChatResponse>('/chat', { message, tone }, { timeout: REQUEST_TIMEOUT_MS })
      const data = res.data
      append({ sender: 'ai', text: data.reply, isCrisis: data.isCrisis, time: nowTime() })
      setDraft(data.parsedData && !data.isCrisis && data.parsedData.options.length >= 2 ? data.parsedData : null)
    } catch (error) {
      const status = (error as { response?: { status?: number } }).response?.status
      // 입력 검증 오류(400)는 다시 시도해도 같으므로 재시도 버튼 없이 안내만
      append({
        sender: 'ai',
        text: errorMessage(error, '서버와 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'),
        isError: true,
        retryText: status === 400 ? undefined : message,
        time: nowTime(),
      })
      if (status === 400) setDraft(null)
    } finally {
      setSending(false)
      inputRef.current?.focus({ preventScroll: true })
    }
  }

  function retry(msg: Message) {
    setMessages((list) => list.filter((m) => m.id !== msg.id))
    void sendMessage(msg.retryText ?? '', { skipUserBubble: true })
  }

  function clearChat() {
    if (sending) return
    if (messages.length > 1 && !window.confirm('대화 내용을 지우고 새로 시작할까요?')) return
    setDraft(null)
    stickRef.current = true
    setMessages([{ id: nextId.current++, sender: 'ai', text: '대화가 초기화되었습니다. 새로운 고민을 말씀해 주세요! 🧭' }])
    setInput('')
    inputRef.current?.focus()
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing && e.keyCode !== 229) {
      e.preventDefault()
      void sendMessage(input)
    }
  }

  return (
    <div className="chat-page">
      <header className="chat-header">
        <div className="chat-header-main">
          <div className="chat-header-icon" aria-hidden="true">
            ✦
          </div>
          <div className="chat-header-copy">
            <h1 className="chat-header-title">AI 의사결정 상담소</h1>
            <p className="chat-header-subtitle">고민을 편하게 이야기해 주세요. 선택지를 함께 정리해 드릴게요.</p>
          </div>
        </div>

        <div className="chat-header-actions">
          <label htmlFor="ai-tone" className="sr-only">
            상담 성향 선택
          </label>
          <select id="ai-tone" className="chat-select" value={tone} onChange={(e) => setTone(e.target.value as Tone)}>
            {TONES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>

          <button type="button" className="chat-clear-btn" onClick={clearChat}>
            ↻ <span>초기화</span>
          </button>
        </div>
      </header>

      <section className="chat-panel">
        <section className="decision-banner" hidden={!draft} aria-live="polite">
          {draft && (
            <div className="decision-banner-inner">
              <div className="decision-banner-copy">
                <h2 className="decision-banner-title">💡 고민을 정리했어요</h2>
                <p className="decision-banner-summary">
                  {draft.title || '고민 요약'} · {draft.category || '기타'}
                </p>
                <div className="decision-chips">
                  {draft.options.slice(0, 2).map((option, i) => (
                    <span key={i} className="decision-chip">
                      {i + 1}. {option}
                    </span>
                  ))}
                </div>
              </div>
              <Link to={decisionLink(draft)} className="decision-link">
                결정 도우미로 이동 →
              </Link>
            </div>
          )}
        </section>

        <div
          ref={containerRef}
          className="chat-messages"
          role="log"
          aria-live="polite"
          aria-label="대화 내용"
          onScroll={() => setShowScrollBtn(!isNearBottom())}
        >
          {messages.map((m) => (
            <article
              key={m.id}
              className={`chat-message ${m.sender}${m.isError ? ' error' : ''}${m.isCrisis ? ' crisis' : ''}`}
            >
              <div className="chat-avatar" aria-hidden="true">
                {m.sender === 'user' ? '나' : 'AI'}
              </div>
              <div className="chat-message-column">
                {m.sender === 'user' ? (
                  <div className="chat-bubble">{m.text}</div>
                ) : (
                  <div className="chat-bubble" dangerouslySetInnerHTML={{ __html: formatText(m.text) }} />
                )}
                {m.retryText && (
                  <button type="button" className="chat-retry-btn" onClick={() => retry(m)}>
                    다시 시도
                  </button>
                )}
                {m.time && <time className="chat-time">{m.time}</time>}
              </div>
            </article>
          ))}
          {sending && (
            <div className="chat-message ai" role="status" aria-live="polite">
              <div className="chat-avatar" aria-hidden="true">
                AI
              </div>
              <div className="chat-message-column">
                <div className="chat-bubble typing-bubble">
                  <span className="typing-label">AI 답변 중</span>
                  <span className="typing-dots" aria-hidden="true">
                    <span></span>
                    <span></span>
                    <span></span>
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>

        <button
          className="scroll-bottom"
          type="button"
          aria-label="최신 메시지로 이동"
          hidden={!showScrollBtn}
          onClick={scrollToBottom}
        >
          ↓
        </button>

        <section className="chat-input-area">
          <div className="quick-area">
            <span className="quick-label">빠른 추천</span>
            {QUICK.map((q) => (
              <button
                key={q.label}
                type="button"
                className="quick-btn"
                disabled={sending}
                onClick={() => {
                  setInput(q.message)
                  void sendMessage(q.message)
                }}
              >
                {q.label}
              </button>
            ))}
          </div>

          <div className="chat-compose">
            <label htmlFor="user-input" className="sr-only">
              고민 내용 입력
            </label>
            <textarea
              id="user-input"
              ref={inputRef}
              rows={1}
              maxLength={MAX_LEN}
              enterKeyHint="send"
              placeholder="어떤 상황인지 구체적으로 적어주세요. (Enter 전송, Shift+Enter 줄바꿈)"
              value={input}
              disabled={sending}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <button id="send-btn" type="button" disabled={sending || !input.trim()} onClick={() => void sendMessage(input)}>
              <span>전송</span>
              <span aria-hidden="true">➤</span>
            </button>
          </div>

          <div className="chat-meta">
            <span>AI 답변은 참고용이며 최종 결정은 본인의 몫이에요.</span>
            <span id="char-count" className={input.length >= MAX_LEN - 50 ? 'near-limit' : undefined}>
              {input.length} / {MAX_LEN}
            </span>
          </div>
        </section>
      </section>
    </div>
  )
}
