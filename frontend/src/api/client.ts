import i18n from "../i18n";
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

/**
 * Builds an API path, URL-encoding every interpolated value:
 *   apiPath`/api/matches/${id}/throws`
 * Values come from the address bar (route params) and from the server. Without encoding, an id like
 * "../admin/users/1/password-reset?" would turn a harmless call into a different, CSRF-protected request
 * (client-side path traversal). Dot segments and empty values are refused outright.
 */
export function apiPath(strings: TemplateStringsArray, ...values: (string | number)[]): string {
  return strings.reduce((out, part, i) => {
    if (i === 0) return part;
    const raw = values[i - 1];
    const value = raw === undefined || raw === null ? "" : String(raw);
    // Shown to the user as the translated BAD_PATH message (e.g. someone opened /matches/..).
    if (value === "" || value === "." || value === "..") throw new ApiError({ code: "BAD_PATH", status: 0 });
    return out + encodeURIComponent(value) + part;
  }, "");
}

/**
 * React Query retry policy. A sleeping free-tier backend can take about a minute to wake, so network errors and
 * 5xx are retried patiently. Client errors (4xx) and bad paths never get better by retrying.
 */
export function shouldRetry(failureCount: number, err: unknown): boolean {
  if (err instanceof ApiError && (err.code === "BAD_PATH" || (err.status >= 400 && err.status < 500))) return false;
  return failureCount < 6;
}

/** Only same-origin /api paths, with no way to climb out of them (also when percent-encoded). */
function isSafePath(path: string): boolean {
  if (!path.startsWith("/api/") || path.startsWith("//") || path.includes("\\")) return false;
  const pathname = path.split(/[?#]/)[0];
  let decoded: string;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return false;
  }
  return !decoded.split(/[/\\]/).some((seg) => seg === ".." || seg === ".");
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
  if (!isSafePath(path)) throw new ApiError({ code: "BAD_PATH", status: 0 });
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
  const data = localiseDeleted(text ? safeJson(text) : undefined, i18n.t("player.deleted"));
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

/**
 * A deleted account is stored as "Slettet spiller" with {@code deleted: true}. Every player object in a response gets
 * the name in the current language instead, so the 50-odd places that show {@code realName} need no special case.
 */
export function localiseDeleted<T>(data: T, label: string): T {
  if (Array.isArray(data)) {
    data.forEach((item) => localiseDeleted(item, label));
  } else if (data && typeof data === "object") {
    const obj = data as Record<string, unknown>;
    if (obj.deleted === true && typeof obj.realName === "string") obj.realName = label;
    for (const value of Object.values(obj)) if (value && typeof value === "object") localiseDeleted(value, label);
  }
  return data;
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
  del: <T>(path: string, body?: unknown) => request<T>("DELETE", path, body),
};
