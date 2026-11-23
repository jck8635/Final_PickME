import { useRef, useState } from "react";
import { Link } from "react-router";
import { Search, LoaderCircle } from "lucide-react";
import { api, errorMessage } from "../api/client";

type SimilarDecision = {
  decisionId: number;
  title: string;
  actualChoice: string | null;
  retrospectiveNote: string | null;
  similarity: number;
};

type SimilarResponse = { results: SimilarDecision[] };

export default function SimilarDecisions() {
  const [text, setText] = useState("");
  const [results, setResults] = useState<SimilarDecision[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef(false);

  async function search(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = text.trim();
    if (!value || inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError(null);
    setResults(null);
    try {
      const response = await api.post<SimilarResponse>("/history/similar", {
        text: value,
      });
      setResults(response.data.results);
    } catch (e) {
      setError(errorMessage(e, "유사 결정 검색에 실패했습니다."));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  return (
    <section
      aria-labelledby="similar-heading"
      className="rounded-xl border border-blue-100 bg-white p-5 shadow-sm space-y-4"
    >
      <div>
        <h2 id="similar-heading" className="text-lg font-bold text-slate-900">
          AI 과거 유사 결정 찾기
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          새 고민을 입력하면 내 과거 기록에서 의미가 비슷한 결정을 찾아요.
        </p>
      </div>
      <form
        onSubmit={(e) => {
          void search(e);
        }}
        className="space-y-3"
      >
        <label htmlFor="similar-query" className="sr-only">
          새로운 고민
        </label>
        <textarea
          id="similar-query"
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={5000}
          rows={3}
          placeholder="예: Java 백엔드와 Python AI 개발 중 무엇을 더 공부할까?"
          className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          type="submit"
          disabled={busy || !text.trim()}
          className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {busy ? (
            <LoaderCircle className="h-4 w-4 animate-spin" />
          ) : (
            <Search className="h-4 w-4" />
          )}
          {busy ? "검색 중..." : "유사 결정 찾기"}
        </button>
      </form>
      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}
      {results && (
        <div aria-live="polite" className="space-y-3">
          <h3 className="text-sm font-semibold text-slate-700">
            검색 결과 {results.length}개
          </h3>
          {results.length === 0 && (
            <p className="text-sm text-slate-500">
              임베딩된 과거 결정 기록이 없습니다.
            </p>
          )}
          {results.map((item) => (
            <article
              key={item.decisionId}
              className="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2"
            >
              <div className="flex items-start justify-between gap-3">
                <h4 className="font-semibold text-slate-900">{item.title}</h4>
                <span className="shrink-0 rounded-md bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700">
                  유사도 {(item.similarity * 100).toFixed(1)}%
                </span>
              </div>
              <p className="text-sm text-slate-700">
                <strong>실제 선택:</strong>{" "}
                {item.actualChoice || "아직 기록하지 않음"}
              </p>
              <p className="text-sm text-slate-700">
                <strong>회고:</strong>{" "}
                {item.retrospectiveNote || "아직 작성하지 않음"}
              </p>
              <Link to="/history" className="text-xs text-blue-700">
                고민 기록 목록에서 확인
              </Link>
            </article>
          ))}
          <p className="text-xs text-slate-500">
            유사도는 문장의 의미적 유사성을 나타내며, 추천의 정확도나 선택의
            정답률이 아닙니다.
          </p>
        </div>
      )}
    </section>
  );
}
