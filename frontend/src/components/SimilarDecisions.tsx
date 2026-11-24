import { useEffect, useState } from "react";
import { api, errorMessage } from "../api/client";

type SimilarDecision = {
  decisionId: number;
  title: string;
  actualChoice: string | null;
  retrospectiveNote: string | null;
  similarity: number | null;
  relevance: number | null;
};

type SimilarResponse = {
  results: SimilarDecision[];
  summary: string | null;
  mode: string;
  notice: string | null;
};

export default function SimilarDecisions({
  text,
  onSelect,
}: {
  text: string;
  onSelect: (id: number) => void;
}) {
  const [results, setResults] = useState<SimilarDecision[] | null>(null);
  const [summary, setSummary] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setBusy(true);
    setResults(null);
    setError(null);
    setSummary(null);
    setNotice(null);
    async function search() {
      try {
        const response = await api.post<SimilarResponse>(
          "/history/similar",
          { text },
          { signal: controller.signal },
        );
        if (!controller.signal.aborted) {
          setResults(response.data.results);
          setSummary(response.data.summary);
          setNotice(response.data.notice);
        }
      } catch (e) {
        if (!controller.signal.aborted)
          setError(errorMessage(e, "유사 결정 검색에 실패했습니다."));
      } finally {
        if (!controller.signal.aborted) setBusy(false);
      }
    }
    void search();
    return () => controller.abort();
  }, [text]);

  return (
    <section
      aria-labelledby="similar-heading"
      className="rounded-xl border border-blue-100 bg-white p-5 shadow-sm space-y-4"
    >
      <h2 id="similar-heading" className="sr-only">
        AI 유사 검색 결과
      </h2>
      {busy && (
        <p role="status" className="text-sm text-slate-500">
          검색 및 AI 관련성 분석 중...
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="text-sm text-amber-700">
          {notice}
        </p>
      )}
      {summary && (
        <div className="rounded-lg bg-blue-50 p-4 space-y-2">
          <h3 className="font-semibold">AI 검색 결과 요약</h3>
          <p className="text-sm whitespace-pre-wrap">{summary}</p>
          <p className="text-xs text-slate-500">
            AI 요약은 원본 기록에서 확인해주세요.
          </p>
        </div>
      )}
      {results && (
        <div aria-live="polite" className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900">검색 결과</h3>
            <span className="text-sm text-slate-500">{results.length}개</span>
          </div>
          {results.length === 0 && (
            <p className="text-sm text-slate-500">
              유사한 과거 고민 기록이 없습니다.
            </p>
          )}
          {results.map((item) => (
            <button
              type="button"
              key={item.decisionId}
              onClick={() => onSelect(item.decisionId)}
              className="block w-full text-left rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2 hover:border-blue-300 hover:shadow-sm focus-visible:ring-2 focus-visible:ring-blue-500 transition-all cursor-pointer"
            >
              <div className="flex items-start justify-between gap-3">
                <h4 className="font-semibold text-slate-900">{item.title}</h4>
                <span className="shrink-0 rounded-md bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700">
                  {item.relevance != null
                    ? `관련성 ${item.relevance}점`
                    : item.similarity != null
                      ? `유사도 ${(item.similarity * 100).toFixed(1)}%`
                      : "키워드 일치"}
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
              <p className="text-xs font-medium text-blue-600">상세 보기 →</p>
            </button>
          ))}
          <p className="text-xs text-slate-500">
            관련성 점수와 벡터 유사도는 검색 지표이며 정확도나 정답률이
            아닙니다.
          </p>
        </div>
      )}
    </section>
  );
}
