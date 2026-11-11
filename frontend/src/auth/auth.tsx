import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { Navigate, Outlet, useLocation } from "react-router";
import { api } from "../api/client";
import { keys, useMe } from "../api/queries";
import type { Me } from "../api/types";
import { Spinner } from "../components/ui";
import i18n, { currentLanguage } from "../i18n";
import { live } from "../realtime/stomp";

/** Current user, or null when logged out. */
export function useCurrentUser(): { me: Me | null; loading: boolean } {
  const q = useMe();
  return { me: q.data ?? null, loading: q.isLoading };
}

export function useAuthActions() {
  const qc = useQueryClient();
  return {
    afterLogin: (me: Me) => {
      qc.setQueryData(keys.me, me);
      if (me.preferredLocale && me.preferredLocale !== currentLanguage()) void i18n.changeLanguage(me.preferredLocale);
      void live.reset();
    },
    logout: async () => {
      try {
        await api.post("/api/auth/logout");
      } finally {
        qc.clear();
        void live.reset();
      }
    },
  };
}

/** Applies the language saved on the account the first time we learn who the user is. */
export function useAccountLanguage(me: Me | null) {
  useEffect(() => {
    if (me?.preferredLocale && me.preferredLocale !== currentLanguage()) {
      void i18n.changeLanguage(me.preferredLocale);
    }
    // only when the user changes, not on every language switch
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me?.id]);
}

export function RequireAuth() {
  const { me, loading } = useCurrentUser();
  const location = useLocation();
  if (loading) return <Spinner />;
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

export function RequireAdmin() {
  const { me, loading } = useCurrentUser();
  if (loading) return <Spinner />;
  if (!me || me.role !== "ADMIN") return <Navigate to="/" replace />;
  return <Outlet />;
}
