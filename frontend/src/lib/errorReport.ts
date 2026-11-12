/**
 * Browser error reporting (see docs/OPERATIONS.md). Errors go to our own backend (POST /api/client-errors), which
 * forwards them to Sentry when SENTRY_DSN is set – no third-party script, no CSP change. Reports carry the message,
 * stack, path (no query string) and app version: never who you are.
 */
export type ErrorKind = "error" | "unhandledrejection" | "boundary";

export interface ErrorReport {
  kind: ErrorKind;
  message: string;
  stack?: string;
  path: string;
  release: string;
}

const MAX_PER_PAGE = 5;
const NOISE = [/ResizeObserver loop/i, /(^|: )Script error\.?$/i, /AbortError/, /Load failed$/];

export function scrubPath(path: string): string {
  const clean = path.split(/[?#]/)[0];
  return (clean || "/").slice(0, 300);
}

function scrubText(text: string): string {
  return text
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, "[email]")
    .replace(/\?[^\s#)"']*/g, "")
    .replace(/#[^\s)"']*/g, "");
}

export function buildReport(kind: ErrorKind, error: unknown, path: string, release: string): ErrorReport {
  let message: string;
  let stack: string | undefined;
  if (error instanceof Error) {
    message = `${error.name}: ${error.message}`;
    stack = error.stack;
  } else if (typeof error === "string" && error) {
    message = error;
  } else {
    message = "Non-error value thrown";
  }
  return {
    kind,
    message: scrubText(message).slice(0, 500) || "Error",
    stack: stack ? scrubText(stack).slice(0, 8000) : undefined,
    path: scrubPath(path),
    release: release.slice(0, 40),
  };
}

/** Builds a reporting function: deduplicated, capped per page load, and silent if sending fails. */
export function createReporter(send: (r: ErrorReport) => void, currentPath: () => string, release: string) {
  const seen = new Set<string>();
  let count = 0;
  return (kind: ErrorKind, error: unknown) => {
    try {
      const report = buildReport(kind, error, currentPath(), release);
      if (NOISE.some((n) => n.test(report.message))) return;
      if (seen.has(report.message) || count >= MAX_PER_PAGE) return;
      seen.add(report.message);
      count++;
      send(report);
    } catch {
      // reporting must never cause an error of its own
    }
  };
}
