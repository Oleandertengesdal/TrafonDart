import type { ApiErrorBody } from "./types";

/**
 * Thin fetch wrapper.
 * - Same-origin only (`/api/...`): Vite proxies in dev, Vercel rewrites in production.
 * - The session lives in an HttpOnly cookie that JavaScript never sees.
 * - Every state-changing request echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header (Spring Security CSRF).
 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly fieldErrors: { field: string; code: string }[];
  readonly details: unknown;

  constructor(body: ApiErrorBody) {
    super(body.code);
    this.code = body.code;
    this.status = body.status;
    this.fieldErrors = body.fieldErrors ?? [];
    this.details = body.details;
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((f) => f.field === field)?.code;
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith(name + "="));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : null;
}

let csrfReady: Promise<void> | null = null;

/**
 * Fetch the CSRF cookie once before any other API call. Without this, the very first parallel requests would
 * each be handed a different token and the first POST could fail.
 */
let bootstrap: Promise<void> | null = null;
function csrfBootstrap(): Promise<void> {
  if (readCookie("XSRF-TOKEN")) return Promise.resolve();
  bootstrap ??= fetch("/api/auth/csrf", { credentials: "same-origin" })
    .then(() => undefined)
    .catch(() => undefined)
    .finally(() => {
      bootstrap = null;
    });
  return bootstrap;
}

async function ensureCsrf(): Promise<string | null> {
  if (!readCookie("XSRF-TOKEN")) {
    csrfReady ??= fetch("/api/auth/csrf", { credentials: "same-origin" }).then(() => undefined);
    await csrfReady;
    csrfReady = null;
  }
  return readCookie("XSRF-TOKEN");
}

async function request<T>(method: string, path: string, body?: unknown, retried = false): Promise<T> {
  await csrfBootstrap();
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method !== "GET") {
    const token = await ensureCsrf();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }
  if (body !== undefined) headers["Content-Type"] = "application/json";

  let res: Response;
  try {
    res = await fetch(path, {
      method,
      headers,
      credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError({ code: "NETWORK_ERROR", status: 0 });
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : undefined;
  if (!res.ok) {
    const err = new ApiError(
      (data as ApiErrorBody | undefined)?.code ? (data as ApiErrorBody) : { code: res.status >= 500 ? "INTERNAL_ERROR" : "UNKNOWN", status: res.status },
    );
    // A stale CSRF cookie (e.g. after logout) – fetch a fresh one and retry once.
    if (err.code === "CSRF_INVALID" && !retried) {
      document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
      return request<T>(method, path, body, true);
    }
    throw err;
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body ?? {}),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body ?? {}),
  del: <T>(path: string) => request<T>("DELETE", path),
};
