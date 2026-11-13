import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, errorMessage } from '../api/client'
import type { ConsultRequest, ConsultResponse, Criterion, DecisionOption, DecisionStyle } from '../api/types'
import { useAuth } from '../lib/app-context'

/** 기존 templates/decision/index.html (한 페이지 3단계 방식) */

const CATEGORIES = ['음식/식사', '진로/직업', '소비/쇼핑', '연애/관계', '일상/생활', '학습/자기계발', '여행/여가', '기타']
const CONDITION_CHIPS = [
  '예산이 정해져 있음',
  '시간이 급함',
  '식사/일정 후 바로 약속이 있음',
  '주변 사람 의견이 중요',
  '건강/체질을 고려해야 함',
]
const CRITERIA_CHIPS = ['비용 (예산)', '시간 효율', '개인 만족도', '성장 가능성 및 기회', '안정성', '주변 사람들의 만족']
const STYLE_LABELS: Record<DecisionStyle, string> = {
  soft: '💕 부드러운 공감형',
  objective: '📊 객관적인 분석형',
  fact: '🧊 냉정한 팩트형',
}
const MAX_OPTIONS = 5

// AI 상담 카테고리 이름 → 결정 도우미 카테고리 이름
const CATEGORY_MAP: Record<string, string> = {
  '진로/이직': '진로/직업',
  '쇼핑/소비': '소비/쇼핑',
}

const emptyOption = (): DecisionOption => ({ title: '', note: '' })

/** AI 상담에서 넘어온 값(쿼리스트링)으로 미리 채우기 */
function readPrefill(params: URLSearchParams) {
  const title = (params.get('title') ?? '').trim()
  const content = (params.get('content') ?? '').trim()
  const rawCategory = (params.get('category') ?? '').trim()
  const category = CATEGORY_MAP[rawCategory] ?? rawCategory
  let options: string[] = []
  try {
    const parsed: unknown = JSON.parse(params.get('options') ?? '[]')
    if (Array.isArray(parsed)) options = parsed.map((x) => String(x).trim()).filter(Boolean).slice(0, MAX_OPTIONS)
  } catch {
    options = []
  }
  const fromChat = Boolean(title || content || options.length)
  return { title, content, categories: CATEGORIES.includes(category) ? [category] : [], options, fromChat }
}

export default function DecisionPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const prefill = useMemo(() => readPrefill(params), [params])

  const [categories, setCategories] = useState<string[]>(prefill.categories)
  const [style, setStyle] = useState<DecisionStyle>('objective')
  const [title, setTitle] = useState(prefill.title)
  const [content, setContent] = useState(prefill.content)
  const [conditions, setConditions] = useState<string[]>([])
  const [conditionsFree, setConditionsFree] = useState('')
  const [options, setOptions] = useState<DecisionOption[]>(() => {
    const opts = prefill.options.map((t) => ({ title: t, note: '' }))
    while (opts.length < 2) opts.push(emptyOption())
    return opts
  })
  const [criteria, setCriteria] = useState<Criterion[]>([])
  const [newCriterion, setNewCriterion] = useState('')

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<string | null>(null)
  const [resultStyle, setResultStyle] = useState<DecisionStyle | null>(null)
  const [restyling, setRestyling] = useState<DecisionStyle | null>(null)
  const [saved, setSaved] = useState(false)
  const resultRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (result) resultRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [result])

  const toggle = (list: string[], value: string) =>
    list.includes(value) ? list.filter((v) => v !== value) : [...list, value]

  function toggleCriterion(name: string) {
    setCriteria((list) =>
      list.some((c) => c.name === name) ? list.filter((c) => c.name !== name) : [...list, { name, weight: '5' }],
    )
  }

  function addCustomCriterion() {
    const name = newCriterion.trim()
    if (!name) {
      window.alert('기준 이름을 입력해주세요.')
      return
    }
    if (criteria.some((c) => c.name === name)) {
      window.alert('이미 추가된 기준이에요.')
      return
    }
    setCriteria((list) => [...list, { name, weight: '5' }])
    setNewCriterion('')
  }

  function updateOption(index: number, patch: Partial<DecisionOption>) {
    setOptions((list) => list.map((o, i) => (i === index ? { ...o, ...patch } : o)))
  }

  function buildRequest(withStyle: DecisionStyle): ConsultRequest {
    return {
      categories,
      style: withStyle,
      title,
      content,
      conditions,
      conditionsFree,
      options: options.filter((o) => o.title.trim()),
      criteria,
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!content.trim()) {
      setError('고민 내용을 입력해 주세요.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const res = await api.post<ConsultResponse>('/decisions/consult', buildRequest(style), { timeout: 120000 })
      setResult(res.data.result)
      setResultStyle(null)
      setSaved(res.data.decisionId != null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  // 결과에서 스타일만 바꿔 다시 상담 (예: "공감은 됐고, 이제 팩트로 말해줘")
  async function restyle(next: DecisionStyle) {
    setRestyling(next)
    try {
      const res = await api.post<{ result: string }>('/decisions/restyle', buildRequest(next), { timeout: 120000 })
      setResult(res.data.result)
      setResultStyle(next)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setRestyling(null)
    }
  }

  return (
    <div className="container mt-4">
      <h2>결정 도우미 (AI 상담)</h2>
      <p className="text-muted">지금 어떤 고민이 있으신가요? AI가 현명한 결정을 도와드립니다.</p>
      {prefill.fromChat && (
        <div
          className="alert"
          style={{
            background: '#eff6ff',
            border: '1px solid #bfdbfe',
            color: '#1e40af',
            borderRadius: 12,
            padding: '12px 16px',
            marginBottom: 16,
          }}
        >
          AI 상담에서 정리한 선택지를 미리 채워 두었어요. 제목·선택지를 확인한 뒤, 판단 기준과 중요도만 골라 주시면 됩니다.
        </div>
      )}

      {error && <div className="alert">{error}</div>}

      {/* 1단계 */}
      <form className="panel" id="decision-form" onSubmit={submit}>
        <h2 className="step-title">📋 1. 고민 개요 및 설정</h2>
        <p className="text-muted">고민 상황과 원하는 상담 스타일을 알려주세요.</p>

        <fieldset className="mb-3">
          <legend>
            🏷️ 고민 카테고리 <small>(여러 개 선택 가능)</small>
          </legend>
          <div className="chip-group">
            {CATEGORIES.map((cat) => (
              <label key={cat} className="chip">
                <input
                  type="checkbox"
                  checked={categories.includes(cat)}
                  onChange={() => setCategories((list) => toggle(list, cat))}
                />
                {cat}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="mb-3">
          <legend>✨ AI 조언 스타일</legend>
          <div className="chip-group">
            {(['soft', 'objective', 'fact'] as const).map((s) => (
              <label key={s} className="chip">
                <input type="radio" name="style" checked={style === s} onChange={() => setStyle(s)} />{' '}
                {s === 'soft' ? '부드러운 공감형' : s === 'objective' ? '객관적인 분석형' : '냉정한 팩트형'}
              </label>
            ))}
          </div>
          <p className="hint">예: 상담 후 "공감은 됐고, 이제 팩트로 말해줘" 처럼 스타일을 바꿔 다시 물어볼 수 있어요.</p>
        </fieldset>

        <div className="mb-3">
          <label htmlFor="title">고민 제목 (자유롭게 적어주세요)</label>
          <input
            type="text"
            id="title"
            maxLength={200}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="예: 지금 이직을 준비해야 할까요?"
          />
        </div>

        <div className="mb-3">
          <label htmlFor="content">
            고민 내용 <b className="req">*</b>
          </label>
          <div className="guide-box">
            <b>이렇게 적어주면 더 정확한 상담이 가능해요 (핵심 4가지)</b>
            <ul>
              <li>
                어떤 상황인지 <small>(예: 직장에서 상사와 갈등이 있음)</small>
              </li>
              <li>
                누구와 관련된 일인지 <small>(예: 상사, 친구, 가족 등)</small>
              </li>
              <li>
                무엇을 고민하고 있는지 <small>(예: 이직 vs 버티기)</small>
              </li>
              <li>
                왜 고민되는지 <small>(예: 연봉은 괜찮은데 스트레스가 커서)</small>
              </li>
            </ul>
          </div>
          <textarea
            id="content"
            rows={6}
            required
            maxLength={5000}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="예: 현재 직장에서 상사와 갈등이 있어요. 이직을 준비할까 말까 고민인데, 떠나기엔 연봉이 괜찮고 남기엔 스트레스가 커요."
          />
        </div>

        <fieldset className="mb-3">
          <legend>
            ➕ 추가 조건 및 특별한 상황 <small>(선택)</small>
          </legend>
          <div className="chip-group">
            {CONDITION_CHIPS.map((chip) => (
              <label key={chip} className="chip">
                <input
                  type="checkbox"
                  checked={conditions.includes(chip)}
                  onChange={() => setConditions((list) => toggle(list, chip))}
                />
                {chip}
              </label>
            ))}
          </div>
          <input
            type="text"
            className="mt-2"
            maxLength={300}
            value={conditionsFree}
            onChange={(e) => setConditionsFree(e.target.value)}
            placeholder="직접 입력: 예산은 15,000원 이내 등"
          />
        </fieldset>
      </form>

      {/* 2단계 */}
      <div className="panel mt-3" id="options-form">
        <h2 className="step-title">✅ 2. 비교할 선택지 작성 (선택)</h2>
        <p className="text-muted">최소 2개에서 최대 5개까지의 대안을 입력하세요. 비어 있어도 상담은 가능해요.</p>
        <div id="option-list">
          {options.map((opt, i) => (
            <div key={i} className="option-card">
              <span className="badge">선택지 {i + 1}</span>
              <div className="row">
                <div>
                  <label htmlFor={`option-title-${i}`}>선택지 이름</label>
                  <input
                    type="text"
                    id={`option-title-${i}`}
                    maxLength={200}
                    value={opt.title}
                    onChange={(e) => updateOption(i, { title: e.target.value })}
                    placeholder={i === 0 ? '예: 짬뽕' : '예: 짜장면'}
                  />
                </div>
                <div>
                  <label htmlFor={`option-note-${i}`}>간단한 설명 및 특징 (선택)</label>
                  <input
                    type="text"
                    id={`option-note-${i}`}
                    maxLength={500}
                    value={opt.note}
                    onChange={(e) => updateOption(i, { note: e.target.value })}
                    placeholder={i === 0 ? '예: 얼큰한 국물, 맵기 조절 가능' : '예: 고소하고 든든한 한 그릇'}
                  />
                </div>
              </div>
            </div>
          ))}
        </div>
        <button
          type="button"
          className="btn-restyle"
          onClick={() => {
            if (options.length >= MAX_OPTIONS) {
              window.alert('최대 5개까지 가능해요.')
              return
            }
            setOptions((list) => [...list, emptyOption()])
          }}
        >
          + 선택지 추가
        </button>
      </div>

      {/* 3단계 */}
      <div className="panel mt-3">
        <h2 className="step-title">⚖️ 3. 판단 기준 및 중요도 가중치</h2>
        <p className="text-muted">아래 칩을 눌러 판단에 필요한 기준을 추가하고, 중요도를 선택하세요.</p>

        <div className="chip-group" id="criteria-chips">
          {CRITERIA_CHIPS.map((name) => (
            <label key={name} className="chip">
              <input
                type="checkbox"
                checked={criteria.some((c) => c.name === name)}
                onChange={() => toggleCriterion(name)}
              />{' '}
              {name}
            </label>
          ))}
        </div>

        <div id="weight-box" className="mt-3">
          {criteria.map((c) => (
            <div key={c.name} className="criteria-row">
              <span
                role="button"
                tabIndex={0}
                title="눌러서 기준 삭제"
                onClick={() => setCriteria((list) => list.filter((x) => x.name !== c.name))}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') setCriteria((list) => list.filter((x) => x.name !== c.name))
                }}
              >
                {' '}
                {c.name} 🗑️
              </span>
              <span className="imp-label">중요도:</span>
              {['1', '2', '3', '4', '5'].map((n) => (
                <label key={n} className="weight-label">
                  <input
                    type="radio"
                    name={`weight_${c.name}`}
                    checked={c.weight === n}
                    onChange={() =>
                      setCriteria((list) => list.map((x) => (x.name === c.name ? { ...x, weight: n } : x)))
                    }
                  />{' '}
                  {n}
                </label>
              ))}{' '}
              <label className="weight-label hold">
                <input
                  type="radio"
                  name={`weight_${c.name}`}
                  checked={c.weight === '보류'}
                  onChange={() =>
                    setCriteria((list) => list.map((x) => (x.name === c.name ? { ...x, weight: '보류' } : x)))
                  }
                />{' '}
                보류
              </label>
            </div>
          ))}
        </div>

        <div className="add-criteria-row mt-3">
          <input
            type="text"
            id="new-criteria"
            maxLength={50}
            value={newCriterion}
            onChange={(e) => setNewCriterion(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
                e.preventDefault()
                addCustomCriterion()
              }
            }}
            placeholder="세부 기준 직접 추가  예: 수납함, 촬영의 느낌 등"
          />
          <button type="button" className="btn-restyle" onClick={addCustomCriterion}>
            기준 추가
          </button>
        </div>
      </div>

      <div className="text-center mt-4">
        <button
          type="submit"
          form="decision-form"
          className="btn btn-primary"
          disabled={loading}
          style={{ borderRadius: 12, border: '2px solid #0d6efd', padding: '14px 32px', fontWeight: 600 }}
        >
          🤖 AI 상담하러 바로 갈 준비 시작
        </button>
        {loading && <span className="loading-text">⏳ AI가 고민을 분석하고 있어요... (10~20초 정도 걸려요)</span>}
      </div>

      {result && (
        <div className="card mt-4" ref={resultRef}>
          <div className="card-header bg-success text-white">
            <strong>🤖 AI 상담 결과{resultStyle ? ` — ${STYLE_LABELS[resultStyle]}` : ''}</strong>
          </div>
          <div className="card-body">
            <p style={{ whiteSpace: 'pre-line' }}>{result}</p>
            <p className="hint">
              {saved ? (
                <>
                  이 상담은 <Link to="/history">고민 기록</Link>에 저장되었어요.
                </>
              ) : user ? null : (
                <>
                  <Link to="/login?next=/decision">로그인</Link>하면 상담 결과가 고민 기록에 저장돼요.
                </>
              )}
            </p>
            <div className="restyle-box">
              <b>다른 스타일로 다시 듣기:</b>
              {(
                [
                  ['soft', '부드럽게 공감받기'],
                  ['objective', '객관적으로 분석받기'],
                  ['fact', '팩트로 말하기'],
                ] as const
              ).map(([s, label]) => (
                <button
                  key={s}
                  type="button"
                  className="btn-restyle"
                  disabled={restyling !== null}
                  onClick={() => void restyle(s)}
                >
                  {restyling === s ? '다시 생각하는 중...' : label}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
