import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { History, Info, Scale, Search, Star, X } from 'lucide-react'
import { api, errorMessage } from '../api/client'
import type { HistoryItem } from '../api/types'
import { useAuth, useFlash } from '../lib/app-context'
import { formatDateTime } from '../lib/format'

/** 기존 templates/history/index.html */

const CATEGORIES = ['음식/식사', '진로/직업', '소비/쇼핑', '연애/관계', '일상/생활', '학습/자기계발', '여행/여가', '기타']
const STYLE_NAMES: Record<string, string> = { soft: '부드러운 공감형', objective: '객관적인 분석형', fact: '냉정한 팩트형' }

type Filters = { q: string; category: string; sort: 'latest' | 'oldest'; bookmarked: boolean }

export default function HistoryPage() {
  const { user, loading: authLoading } = useAuth()

  if (authLoading) return null
  if (!user) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 py-10">
        <div className="flex flex-col items-center justify-center p-12 text-center border border-dashed border-slate-200 rounded-xl bg-slate-50/50">
          <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center mb-4">
            <History className="w-6 h-6" />
          </div>
          <h3 className="text-base font-semibold text-slate-900 mb-1">로그인하면 고민 기록을 볼 수 있어요</h3>
          <p className="text-sm text-slate-500 max-w-sm mb-6 leading-relaxed">
            결정 도우미로 상담한 내용이 나만의 기록으로 저장됩니다.
          </p>
          <Link
            to="/login?next=/history"
            className="inline-flex items-center justify-center font-medium rounded-lg text-sm px-4 h-10 bg-blue-600 text-white hover:bg-blue-700 shadow-sm"
          >
            로그인하기
          </Link>
        </div>
      </div>
    )
  }
  return <HistoryList />
}

function HistoryList() {
  const { flash } = useFlash()
  const [filters, setFilters] = useState<Filters>({ q: '', category: '전체', sort: 'latest', bookmarked: false })
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<HistoryItem[] | null>(null)
  const [detail, setDetail] = useState<HistoryItem | null>(null)

  const load = useCallback(async () => {
    try {
      const res = await api.get<HistoryItem[]>('/history', {
        params: { q: filters.q, category: filters.category, sort: filters.sort, bookmarked: filters.bookmarked },
      })
      setItems(res.data)
    } catch (error) {
      flash(errorMessage(error, '기록을 불러오지 못했어요.'))
      setItems([])
    }
  }, [filters, flash])

  useEffect(() => {
    void load()
  }, [load])

  // 검색 디바운스 (300ms)
  useEffect(() => {
    const t = window.setTimeout(() => setFilters((f) => (f.q === query.trim() ? f : { ...f, q: query.trim() })), 300)
    return () => window.clearTimeout(t)
  }, [query])

  async function toggleBookmark(item: HistoryItem) {
    try {
      const res = await api.post<{ bookmarked: boolean }>(`/history/${item.id}/bookmark`)
      if (filters.bookmarked && !res.data.bookmarked) {
        void load()
      } else {
        setItems((list) => list?.map((i) => (i.id === item.id ? { ...i, bookmarked: res.data.bookmarked } : i)) ?? null)
      }
    } catch (error) {
      flash(errorMessage(error))
    }
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-10 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <div className="flex items-center gap-2 text-blue-700 text-xs font-semibold mb-1">
            <History className="w-4 h-4" />
            <span>나의 고민 회고 및 보관함</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">고민 분석 기록</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            이전 고민과 선택지 비교 결과를 검색하고, 실제 선택과 만족도를 회고해 보세요.
          </p>
        </div>
      </div>

      <div className="rounded-lg bg-slate-50 border border-slate-200 p-3 flex items-start gap-2.5 text-xs text-slate-600">
        <Info className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
        <p className="leading-relaxed">
          <strong>보안 안내:</strong> 분석 기록은 로그인한 본인만 볼 수 있도록 서버에 보관됩니다. 기록을 눌러 실제로 어떤
          선택을 했는지 <strong>회고</strong>를 남겨 보세요.
        </p>
      </div>

      <div className="flex flex-col sm:flex-row gap-3 bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
        <div className="flex-1 relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="고민 제목이나 내용 검색..."
            maxLength={100}
            className="w-full pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow"
          />
        </div>
        <div className="flex gap-2 flex-wrap">
          <select
            value={filters.category}
            onChange={(e) => setFilters((f) => ({ ...f, category: e.target.value }))}
            className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 cursor-pointer"
          >
            <option value="전체">전체 카테고리</option>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <select
            value={filters.sort}
            onChange={(e) => setFilters((f) => ({ ...f, sort: e.target.value as Filters['sort'] }))}
            className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 cursor-pointer"
          >
            <option value="latest">최신순</option>
            <option value="oldest">오래된순</option>
          </select>
          <button
            type="button"
            aria-pressed={filters.bookmarked}
            onClick={() => setFilters((f) => ({ ...f, bookmarked: !f.bookmarked }))}
            className={`flex items-center gap-1.5 px-3 py-2 border rounded-lg text-sm transition-colors focus:outline-none ${
              filters.bookmarked
                ? 'bg-blue-50 text-blue-600 border-blue-200'
                : 'bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100'
            }`}
          >
            <Star className={`w-4 h-4 ${filters.bookmarked ? 'fill-blue-600' : ''}`} />
            <span className="whitespace-nowrap">즐겨찾기</span>
          </button>
        </div>
      </div>

      {items && items.length > 0 && (
        <div className="space-y-4">
          {items.map((item) => (
            <div
              key={item.id}
              role="button"
              tabIndex={0}
              onClick={() => setDetail(item)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') setDetail(item)
              }}
              className="p-5 bg-white border border-slate-200 rounded-xl shadow-sm hover:shadow-md transition-shadow cursor-pointer relative"
            >
              <div className="flex justify-between items-start mb-2">
                <span className="px-2 py-1 bg-blue-100 text-blue-700 text-[11px] font-bold rounded-md">
                  {item.category || '일반'}
                </span>
                <div className="flex items-center gap-3">
                  <span className="text-xs text-slate-400">{formatDateTime(item.createdAt)}</span>
                  <button
                    type="button"
                    title="즐겨찾기"
                    aria-label={item.bookmarked ? '즐겨찾기 해제' : '즐겨찾기'}
                    className="focus:outline-none transition-colors bg-transparent p-0"
                    onClick={(e) => {
                      e.stopPropagation()
                      void toggleBookmark(item)
                    }}
                  >
                    <Star
                      className={`w-5 h-5 ${
                        item.bookmarked ? 'text-yellow-400 fill-yellow-400' : 'text-slate-300 hover:text-yellow-400'
                      }`}
                    />
                  </button>
                </div>
              </div>
              <h2 className="text-lg font-bold text-slate-900 mb-1 line-clamp-1">{item.title}</h2>
              <p className="text-sm text-slate-500 line-clamp-2">{item.dilemma}</p>
              {item.actualChoice && (
                <p className="text-xs text-emerald-700 mt-2">
                  ✔ 실제 선택: {item.actualChoice}
                  {item.satisfactionScore ? ` · 만족도 ${item.satisfactionScore}점` : ''}
                </p>
              )}
            </div>
          ))}
        </div>
      )}

      {items && items.length === 0 && (
        <div className="flex flex-col items-center justify-center p-12 text-center border border-dashed border-slate-200 rounded-xl bg-slate-50/50">
          <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center mb-4">
            <Scale className="w-6 h-6" />
          </div>
          <h3 className="text-base font-semibold text-slate-900 mb-1">
            {filters.q || filters.category !== '전체' || filters.bookmarked
              ? '조건에 맞는 기록이 없습니다'
              : '아직 작성된 고민 기록이 없습니다'}
          </h3>
          <p className="text-sm text-slate-500 max-w-sm mb-6 leading-relaxed">
            어떤 결정을 내려야 할지 고민되는 순간이 있다면, 선택지 비교를 시작해 보세요!
          </p>
          <Link
            to="/decision"
            className="inline-flex items-center justify-center font-medium rounded-lg transition-all duration-150 whitespace-nowrap text-sm px-4 py-2 gap-2 h-10 bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800 shadow-sm"
          >
            새로운 고민 시작하기
          </Link>
        </div>
      )}

      {detail && (
        <DetailModal
          item={detail}
          onClose={() => setDetail(null)}
          onSaved={(updated) => {
            setItems((list) => list?.map((i) => (i.id === updated.id ? updated : i)) ?? null)
            setDetail(null)
            flash('회고가 저장되었습니다!')
          }}
          onDeleted={(id) => {
            setItems((list) => list?.filter((i) => i.id !== id) ?? null)
            setDetail(null)
            flash('기록을 삭제했습니다.')
          }}
        />
      )}

      <footer className="border-t border-slate-200 mt-10 pt-6 text-xs text-slate-500">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="text-blue-600 font-bold">🛡️</span>
            <span>
              안내: PickMe AI의 분석 및 추천은 합리적 판단을 돕기 위한 <strong>참고용</strong>이며, 모든 최종 결정권과
              책임은 사용자 본인에게 있습니다.
            </span>
          </div>
          <div className="shrink-0 text-slate-400">&copy; 2026 PickMe AI. All rights reserved.</div>
        </div>
      </footer>
    </div>
  )
}

function DetailModal(props: {
  item: HistoryItem
  onClose: () => void
  onSaved: (item: HistoryItem) => void
  onDeleted: (id: number) => void
}) {
  const { item, onClose } = props
  const { flash } = useFlash()
  const [actualChoice, setActualChoice] = useState(item.actualChoice ?? '')
  const [score, setScore] = useState(item.satisfactionScore ? String(item.satisfactionScore) : '')
  const [note, setNote] = useState(item.retrospectiveNote ?? '')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  async function save() {
    setBusy(true)
    try {
      const res = await api.put<HistoryItem>(`/history/${item.id}/retrospective`, {
        actualChoice: actualChoice.trim(),
        satisfactionScore: score,
        retrospectiveNote: note.trim(),
      })
      props.onSaved(res.data)
    } catch (error) {
      flash(errorMessage(error, '저장에 실패했습니다.'))
      setBusy(false)
    }
  }

  async function remove() {
    if (!window.confirm('이 기록을 정말 삭제하시겠습니까?')) return
    setBusy(true)
    try {
      await api.delete(`/history/${item.id}`)
      props.onDeleted(item.id)
    } catch (error) {
      flash(errorMessage(error, '삭제에 실패했습니다.'))
      setBusy(false)
    }
  }

  const hasResult = Boolean(item.result) || item.options.length > 0

  return (
    <div
      className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="modalTitle"
        className="bg-white rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto"
      >
        <div className="sticky top-0 bg-white border-b border-slate-200 px-6 py-4 flex justify-between items-center rounded-t-2xl z-10">
          <h2 id="modalTitle" className="text-xl font-bold text-slate-900">
            {item.title || '제목 없음'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="text-slate-400 hover:text-slate-600 transition-colors bg-transparent p-0"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="p-6 space-y-5">
          <div className="flex items-center gap-3 text-sm flex-wrap">
            <span className="px-2.5 py-1 bg-blue-100 text-blue-700 text-xs font-bold rounded-md">
              {item.category || '일반'}
            </span>
            <span className="text-slate-400">{formatDateTime(item.createdAt)}</span>
            {item.style && <span className="text-slate-400">· {STYLE_NAMES[item.style] ?? item.style}</span>}
          </div>

          <div>
            <h3 className="text-sm font-semibold text-slate-500 mb-1">고민 내용</h3>
            <p className="text-slate-800 leading-relaxed whitespace-pre-wrap">{item.dilemma}</p>
          </div>

          {(item.criteria && item.criteria !== '지정 없음') || item.conditions ? (
            <div className="text-sm text-slate-600 space-y-1">
              {item.criteria && item.criteria !== '지정 없음' && (
                <p>
                  <strong>판단 기준:</strong> {item.criteria}
                </p>
              )}
              {item.conditions && (
                <p>
                  <strong>추가 조건:</strong> {item.conditions}
                </p>
              )}
            </div>
          ) : null}

          {hasResult && (
            <div>
              <h3 className="text-sm font-semibold text-slate-500 mb-2">AI 분석 결과</h3>
              <div className="bg-slate-50 rounded-xl p-4 text-sm text-slate-700 space-y-2">
                {item.options.length > 0 && (
                  <>
                    <p>
                      <strong>선택지:</strong>
                    </p>
                    <ul className="list-disc list-inside space-y-1">
                      {item.options.map((o, i) => (
                        <li key={i}>
                          {o.title}
                          {o.note ? ` - ${o.note}` : ''}
                        </li>
                      ))}
                    </ul>
                  </>
                )}
                {item.result && <p className="whitespace-pre-line mt-2">{item.result}</p>}
              </div>
            </div>
          )}

          <div className="border-t border-slate-200 pt-5">
            <h3 className="text-sm font-semibold text-slate-500 mb-3">나의 회고</h3>
            <div className="space-y-4">
              <div>
                <label htmlFor="actualChoice" className="block text-xs font-medium text-slate-600 mb-1">
                  실제로 선택한 것
                </label>
                <input
                  type="text"
                  id="actualChoice"
                  maxLength={500}
                  value={actualChoice}
                  onChange={(e) => setActualChoice(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="실제로 어떤 선택을 했나요?"
                />
              </div>
              <div>
                <label htmlFor="satisfaction" className="block text-xs font-medium text-slate-600 mb-1">
                  만족도 (1~5점)
                </label>
                <select
                  id="satisfaction"
                  value={score}
                  onChange={(e) => setScore(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">선택 안 함</option>
                  <option value="1">1점 - 매우 불만족</option>
                  <option value="2">2점 - 불만족</option>
                  <option value="3">3점 - 보통</option>
                  <option value="4">4점 - 만족</option>
                  <option value="5">5점 - 매우 만족</option>
                </select>
              </div>
              <div>
                <label htmlFor="retrospectiveNote" className="block text-xs font-medium text-slate-600 mb-1">
                  회고 노트
                </label>
                <textarea
                  id="retrospectiveNote"
                  rows={3}
                  maxLength={2000}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="이 결정에 대해 느낀 점이나 배운 점을 적어보세요."
                />
              </div>
            </div>
          </div>
        </div>

        <div className="sticky bottom-0 bg-white border-t border-slate-200 px-6 py-4 flex justify-between gap-3 rounded-b-2xl">
          <button
            type="button"
            disabled={busy}
            onClick={() => void remove()}
            className="px-4 py-2 text-sm text-red-600 bg-transparent hover:bg-red-50 rounded-lg transition-colors"
          >
            삭제
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm text-slate-600 bg-transparent hover:bg-slate-100 rounded-lg transition-colors"
            >
              닫기
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => void save()}
              className="px-5 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              회고 저장
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
