import { useCallback } from "react";
import { useLocation, useNavigate } from "react-router";

import { errorMessage, isUnauthorized } from "../api/client";
import { useAuth, useFlash } from "../lib/app-context";

export const CATEGORIES = [
  "음식/식사",
  "진로/이직",
  "쇼핑/소비",
  "연애/관계",
  "일상/생활",
  "학습/자기계발",
  "여행/여가",
  "기타",
];

export const LOGIN_REQUIRED =
  "투표, 공감, 댓글, 신고, 게시글 작성은 로그인 후 이용할 수 있습니다.";

export type Run = (
  action: () => Promise<{ data: unknown }>,
  success?: string,
  options?: { refresh?: boolean; onError?: (message: string) => void },
) => Promise<boolean>;

export function useCommunityAction(load: () => Promise<void>): Run {
  const { user } = useAuth();
  const { flash } = useFlash();
  const navigate = useNavigate();
  const { pathname, search } = useLocation();

  return useCallback(
    async (action, success, options) => {
      const login = () => {
        flash(LOGIN_REQUIRED);
        navigate(`/login?next=${encodeURIComponent(pathname + search)}`);
      };
      if (!user) {
        login();
        return false;
      }

      try {
        const { data } = await action();
        const warning = (data as { warning?: unknown } | null)?.warning;
        if (typeof warning === "string" && warning) flash(warning);
        else if (success) flash(success);
        if (options?.refresh !== false) await load();
        return true;
      } catch (error) {
        if (isUnauthorized(error)) {
          options?.onError?.(LOGIN_REQUIRED);
          login();
        } else {
          const message = errorMessage(error);
          if (options?.onError) options.onError(message);
          else flash(message);
        }
        return false;
      }
    },
    [user, flash, navigate, pathname, search, load],
  );
}
