import { useEffect, useState } from "react";
import { api, errorMessage } from "../api/client";

type Stats = {
  summary: { totalDecisions: number; retrospectiveCount: number; averageSatisfaction: number | null; retrospectiveRate: number };
  categories: { category: string; count: number }[];
  monthly: { month: string; count: number; averageSatisfaction: number | null }[];
};
const colors = ["#2563eb", "#0d9488", "#d97706", "#7c3aed", "#db2777", "#64748b", "#0891b2", "#65a30d"];

export default function HistoryStats({ refreshKey }: { refreshKey: number }) {
  const [data, setData] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    api.get<Stats>("/history/stats", { signal: controller.signal })
      .then(res => { if (!controller.signal.aborted) setData(res.data); })
      .catch(err => { if (!controller.signal.aborted) setError(errorMessage(err, "통계를 불러오지 못했어요.")); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [refreshKey, retry]);
  if (loading) return <section className="rounded-xl border border-slate-200 p-4 text-sm text-slate-500" role="status">통계를 불러오는 중...</section>;
  if (error || !data) return <section className="rounded-xl border border-slate-200 p-4 text-sm"><p role="alert">{error || "통계가 없습니다."}</p><button type="button" className="mt-2 text-blue-600" onClick={() => setRetry(n => n + 1)}>다시 시도</button></section>;
  const { summary, categories, monthly } = data;
  const total = summary.totalDecisions;
  let offset = 0;
  const segments = categories.map((item, i) => {
    const start = offset; offset += total ? item.count / total * 100 : 0;
    return `${colors[i % colors.length]} ${start}% ${offset}%`;
  });
  const max = Math.max(1, ...monthly.map(m => m.count));
  const x = (i: number) => 40 + i * 56;
  const y = (score: number) => 120 - score * 20;
  return <section className="bg-white rounded-xl border border-slate-200 p-4 sm:p-6 space-y-5 shadow-sm">
    <div><h2 className="text-lg font-bold text-slate-900">나의 고민 통계</h2><p className="text-xs text-slate-500">검색 조건과 관계없이 전체 고민 기록을 집계합니다.</p></div>
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
      {[["전체 고민", `${total}개`], ["회고 작성", `${summary.retrospectiveCount}개`], ["평균 만족도", summary.averageSatisfaction === null ? "미입력" : `${summary.averageSatisfaction.toFixed(2)} / 5`], ["회고 작성률", `${summary.retrospectiveRate}%`]].map(([label, value]) => <div key={label} className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">{label}</p><p className="mt-1 text-xl font-bold text-slate-900">{value}</p></div>)}
    </div>
    {total === 0 ? <p className="text-sm text-slate-500">고민 기록을 작성하면 통계가 표시됩니다.</p> : <div className="space-y-3"><h3 className="text-sm font-semibold">카테고리별 고민 분포</h3><div className="flex flex-wrap items-center gap-5"><div className="w-36 h-36 rounded-full flex items-center justify-center shrink-0" style={{background: `conic-gradient(${segments.join(",")})`}} aria-hidden="true"><div className="w-24 h-24 bg-white rounded-full flex items-center justify-center font-bold">{total}개</div></div><div className="flex-1 min-w-40 space-y-2">{categories.map((c,i) => <div key={c.category} className="flex items-center gap-2 text-sm"><span className="w-3 h-3 rounded-sm" style={{background: colors[i % colors.length]}}/><span>{c.category}</span><span className="ml-auto">{c.count}개 ({(c.count / total * 100).toFixed(1)}%)</span></div>)}</div></div></div>}
    <div className="grid sm:grid-cols-2 gap-5">
      <div><h3 className="text-sm font-semibold mb-3">최근 6개월 고민 수</h3><div className="flex items-end gap-2 h-40 border-b border-slate-200">{monthly.map(m => <div key={m.month} className="flex-1 flex flex-col items-center justify-end h-full min-w-0"><span className="text-xs">{m.count}</span><div className="w-3/4 bg-blue-500 rounded-t" style={{height: `${m.count / max * 75}%`}}/><span className="text-xs text-slate-500 py-1">{m.month.slice(5)}월</span></div>)}</div></div>
      <div><h3 className="text-sm font-semibold mb-3">월별 평균 만족도</h3><svg viewBox="0 0 350 150" className="w-full" role="img" aria-label="월별 평균 만족도"><title>월별 평균 만족도</title>{[0,1,2,3,4,5].map(v => <g key={v}><line x1="30" x2="335" y1={y(v)} y2={y(v)} stroke="#e2e8f0"/><text x="12" y={y(v)+4} fontSize="10" fill="#64748b">{v}</text></g>)}{monthly.map((m,i) => { const prev = monthly[i-1]?.averageSatisfaction; const score = m.averageSatisfaction; return <g key={m.month}>{i > 0 && prev != null && score != null && <line x1={x(i-1)} y1={y(prev)} x2={x(i)} y2={y(score)} stroke="#0d9488" strokeWidth="2"/>}{score != null && <><circle cx={x(i)} cy={y(score)} r="4" fill="#0d9488"><title>{m.month}: {score}점</title></circle><text x={x(i)} y={y(score)-7} textAnchor="middle" fontSize="10" fill="#0d9488">{score}</text></>}<text x={x(i)} y="140" textAnchor="middle" fontSize="10" fill="#64748b">{m.month.slice(5)}월</text></g>; })}</svg><p className="text-xs text-slate-500">만족도 미입력 월은 표시하지 않습니다. 고민 작성 월 기준입니다.</p></div>
    </div>
  </section>;
}
