import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import type { ReactNode } from "react";
import { api } from "../api/client";
import type { Member } from "../api/types";

// ─────────────────────────────
// 로그인 상태
// ─────────────────────────────

type AdminSession = {
  username: string;
};

type AuthState = {
  user: Member | null;
  admin: AdminSession | null;
  loading: boolean;

  setUser: (user: Member | null) => void;
  setAdmin: (admin: AdminSession | null) => void;

  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<Member | null>(null);
  const [admin, setAdminState] = useState<AdminSession | null>(null);
  const [loading, setLoading] = useState(true);

  // 일반 회원으로 로그인하면 관리자 표시를 해제합니다.
  const setUser = useCallback((nextUser: Member | null) => {
    setUserState(nextUser);

    if (nextUser) {
      setAdminState(null);
    }
  }, []);

  // 관리자로 로그인하면 일반 회원 표시를 해제합니다.
  const setAdmin = useCallback((nextAdmin: AdminSession | null) => {
    setAdminState(nextAdmin);

    if (nextAdmin) {
      setUserState(null);
    }
  }, []);

  // 일반 회원과 관리자 로그인 상태를 함께 확인합니다.
  const refresh = useCallback(async () => {
    try {
      const [memberResult, adminResult] = await Promise.allSettled([
        api.get<{ user: Member | null }>("/auth/me"),

        api.get<{
          admin: AdminSession | null;
          configured: boolean;
        }>("/admin/auth/me"),
      ]);

      const nextUser =
        memberResult.status === "fulfilled"
          ? memberResult.value.data.user
          : null;

      const nextAdmin =
        adminResult.status === "fulfilled"
          ? adminResult.value.data.admin
          : null;

      setAdminState(nextAdmin);
      setUserState(nextAdmin ? null : nextUser);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const value = useMemo(
    () => ({
      user,
      admin,
      loading,
      setUser,
      setAdmin,
      refresh,
    }),
    [user, admin, loading, setUser, setAdmin, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("AuthProvider 안에서 사용하세요.");
  }

  return context;
}

// ─────────────────────────────
// 알림 메시지
// ─────────────────────────────

type Flash = {
  id: number;
  text: string;
};

type FlashState = {
  messages: Flash[];
  flash: (text: string) => void;
  dismiss: (id: number) => void;
};

const FlashContext = createContext<FlashState | null>(null);

export function FlashProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<Flash[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setMessages((list) => list.filter((message) => message.id !== id));
  }, []);

  const flash = useCallback(
    (text: string) => {
      const id = nextId.current++;

      setMessages((list) => [...list.slice(-2), { id, text }]);

      window.setTimeout(() => dismiss(id), 5000);
    },
    [dismiss],
  );

  const value = useMemo(
    () => ({
      messages,
      flash,
      dismiss,
    }),
    [messages, flash, dismiss],
  );

  return (
    <FlashContext.Provider value={value}>{children}</FlashContext.Provider>
  );
}

export function useFlash(): FlashState {
  const context = useContext(FlashContext);

  if (!context) {
    throw new Error("FlashProvider 안에서 사용하세요.");
  }

  return context;
}
