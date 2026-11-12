import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router";
import "./i18n";
import "./index.css";
import { router } from "./App";
import { shouldRetry } from "./api/client";
import { installGlobalErrorHandlers } from "./lib/reporting";

installGlobalErrorHandlers();

// The installable app: cache the app shell so it opens instantly (see public/sw.js). Production only – in development
// a service worker would serve stale code.
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", () => void navigator.serviceWorker.register("/sw.js").catch(() => undefined));
}

const queryClient = new QueryClient({
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
