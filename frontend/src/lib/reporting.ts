import { api } from "../api/client";
import { createReporter, type ErrorKind } from "./errorReport";

declare const __APP_VERSION__: string;
const release = typeof __APP_VERSION__ === "string" ? __APP_VERSION__ : "dev";

/** Reports a browser error to our backend (→ Sentry when configured). Never throws, never retries. */
export const reportError: (kind: ErrorKind, error: unknown) => void = createReporter(
  (report) => {
    void api.post("/api/client-errors", report).catch(() => undefined);
  },
  () => window.location.pathname,
  release,
);

/** Catches what slips through everything else: uncaught errors and rejected promises. */
export function installGlobalErrorHandlers() {
  window.addEventListener("error", (e) => reportError("error", e.error ?? e.message));
  window.addEventListener("unhandledrejection", (e) => reportError("unhandledrejection", e.reason));
  // After a deploy an old page may ask for a chunk that no longer exists: reload once to get the new version.
  window.addEventListener("vite:preloadError", (e) => {
    const k = "td.reloadedForChunk";
    if (sessionStorage.getItem(k)) return;
    sessionStorage.setItem(k, "1");
    e.preventDefault();
    window.location.reload();
  });
}
