import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router";
import "./i18n";
import "./index.css";
import { router } from "./App";
import { shouldRetry } from "./api/client";
import { keys } from "./api/queries";
import { markSessionExpired } from "./auth/sessionExpiry";
import { shouldExpireSession } from "./lib/errors";
import { installGlobalErrorHandlers } from "./lib/reporting";

installGlobalErrorHandlers();

// The installable app: cache the app shell so it opens instantly (see public/sw.js). Production only – in development
// a service worker would serve stale code.
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", () => void navigator.serviceWorker.register("/sw.js").catch(() => undefined));
}

// A session can expire (or be revoked from another device) while a page is open. The first request that is refused as
// "not logged in" forgets the user, so RequireAuth sends them to the login page, which says why and brings them back.
function expireSession(err: unknown, queryKey?: readonly unknown[]) {
  if (!shouldExpireSession(err, queryKey) || queryClient.getQueryData(keys.me) == null) return;
  markSessionExpired();
  queryClient.setQueryData(keys.me, null);
}

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: (err, query) => expireSession(err, query.queryKey) }),
  mutationCache: new MutationCache({ onError: (err) => expireSession(err) }),
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      // Don't hammer the server on 4xx – only retry network/5xx (e.g. a free-tier server waking up).
      // A sleeping free-tier backend can take ~1 minute to wake, so be patient with 5xx/network errors.
      retry: shouldRetry,
      retryDelay: (attempt) => Math.min(2000 * 2 ** attempt, 15_000),
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
