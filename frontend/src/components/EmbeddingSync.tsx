import { useState } from "react";
import { RefreshCw } from "lucide-react";
import { api, errorMessage } from "../api/client";

interface BatchResult {
  total: number;
  success: number;
  failed: number;
  failedIds: number[];
}

export default function EmbeddingSync() {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function handleSync() {
    if (loading) return;

    setLoading(true);
    setMessage("");
    setError("");

    try {
      const res = await api.post<BatchResult>("/history/embedding/batch", {});

      const result = res.data;

      if (result.total === 0) {
        setMessage("동기화할 고민 기록이 없습니다.");
      } else if (result.failed > 0) {
        setError(
          `${result.success}개 반영 완료, ` +
            `${result.failed}개 실패. 다시 시도해주세요.`,
        );
      } else {
        setMessage(
          `${result.success}개의 고민 기록이 반영되었습니다.` +
            (result.total === 10
              ? " 남은 기록이 있다면 다시 동기화해주세요."
              : ""),
        );
      }
    } catch (err) {
      setError(errorMessage(err, "기록 동기화에 실패했습니다."));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">
            AI 검색 기록 동기화
          </h3>

          <p className="text-xs text-slate-500 mt-1">
            아직 AI 검색에 반영되지 않은 고민을 업데이트합니다.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void handleSync()}
          disabled={loading}
          className="flex items-center justify-center gap-2 px-4 py-2 text-sm bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 disabled:opacity-50 transition-colors"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />

          {loading ? "동기화 중..." : "동기화"}
        </button>
      </div>

      {message && (
        <p role="status" className="mt-3 text-xs text-emerald-700">
          {message}
        </p>
      )}

      {error && (
        <p role="alert" className="mt-3 text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
