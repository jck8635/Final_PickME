import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router";
import { Flag } from "lucide-react";
import { api, errorMessage, isUnauthorized } from "../api/client";
import { useAuth, useFlash } from "../lib/app-context";

type Props = {
  postId: number;
  enabled: boolean;
};

export default function ReportButton({ postId, enabled }: Props) {
  const { user } = useAuth();
  const { flash } = useFlash();
  const navigate = useNavigate();

  const dialog = useRef<HTMLDialogElement>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  if (!enabled) return null;

  function open() {
    if (!user) {
      flash("로그인 후 신고할 수 있습니다.");
      navigate("/login?next=/community");
      return;
    }

    dialog.current?.showModal();
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);

    try {
      await api.post(`/community/posts/${postId}/reports`, { reason });
      flash("신고가 접수되었습니다.");
      setReason("");
      dialog.current?.close();
    } catch (e) {
      flash(errorMessage(e));

      if (isUnauthorized(e)) {
        navigate("/login?next=/community");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button
        type="button"
        className="community-report-button"
        disabled={busy}
        onClick={open}
      >
        <Flag size={13} /> 신고
      </button>

      <dialog ref={dialog} className="post-dialog">
        <form onSubmit={submit}>
          <h2>게시글 신고</h2>

          <label>
            신고 사유
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
              rows={4}
              required
              placeholder="신고 사유를 구체적으로 작성해주세요."
            />
          </label>

          <p>관리자가 게시글 내용과 신고 사유를 확인한 뒤 처리합니다.</p>

          <div className="dialog-actions">
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => dialog.current?.close()}
            >
              취소
            </button>
            <button type="submit" disabled={busy || !reason.trim()}>
              {busy ? "접수 중…" : "신고 접수"}
            </button>
          </div>
        </form>
      </dialog>
    </>
  );
}
