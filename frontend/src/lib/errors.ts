import { ApiError } from "../api/client";

/**
 * Every error the app can show, sorted into a handful of kinds that each get their own title, explanation and way out
 * (see ErrorNotice). The precise code is kept so known business errors ("someone else scored at the same time") keep
 * their own sentence.
 */
export type ErrorKind =
  | "offline" // this device has no network
  | "waking" // the free server is asleep or restarting: wait a minute
  | "session" // not logged in any more
  | "forbidden"
  | "notFound"
  | "rateLimited"
  | "server" // the server crashed on this request
  | "conflict" // someone changed it in the meantime
  | "invalid" // the request itself was wrong (usually a form)
  | "unknown";

export type ErrorAction = "login" | "home" | "reload" | null;

export interface ErrorDescription {
  kind: ErrorKind;
  /** Whether trying the same thing again can help. */
  retry: boolean;
  /** The way out to offer besides retrying. */
  action: ErrorAction;
  /** The API error code, or UNKNOWN. */
  code: string;
  /** True when the code has a sentence of its own that says more than the kind's general explanation. */
  specific: boolean;
}

/** Codes whose message would only repeat what the kind already says. */
const GENERIC = new Set(["UNKNOWN", "NETWORK_ERROR", "INTERNAL_ERROR", "NOT_FOUND", "FORBIDDEN", "AUTH_REQUIRED", "AUTH_SESSION_REVOKED", "BAD_PATH", "RATE_LIMITED", "CONFLICT", "CSRF_INVALID"]);

const make = (kind: ErrorKind, retry: boolean, action: ErrorAction, code: string): ErrorDescription => ({
  kind,
  retry,
  action,
  code,
  specific: !GENERIC.has(code) && (kind === "conflict" || kind === "invalid" || kind === "forbidden" || kind === "session"),
});

/**
 * @param online   navigator.onLine at the time (tells "you are offline" apart from "the server is asleep")
 * @param resource true on a detail page loading one thing by the id in the address: a malformed id then means "not found"
 */
export function describeError(err: unknown, { online, resource = false }: { online: boolean; resource?: boolean }): ErrorDescription {
  if (!(err instanceof ApiError)) return make("unknown", true, null, "UNKNOWN");
  const { code, status } = err;
  if (code === "NETWORK_ERROR") return make(online ? "waking" : "offline", true, null, code);
  if (status === 502 || status === 503 || status === 504) return make("waking", true, null, code);
  if (resource && (code === "VALIDATION_FAILED" || code === "BAD_PATH")) return make("notFound", false, "home", code);
  if (code === "CSRF_INVALID") return make("session", false, "reload", code);
  if (status === 401 && code !== "AUTH_INVALID_CREDENTIALS") return make("session", false, "login", code);
  if (status === 403) return make("forbidden", false, "home", code);
  if (status === 404) return make("notFound", false, "home", code);
  if (status === 409) return make("conflict", false, null, code);
  if (status === 429) return make("rateLimited", true, null, code);
  if (status >= 500) return make("server", true, null, code);
  if (status >= 400 || code === "BAD_PATH") return make("invalid", false, null, code);
  return make("unknown", true, null, code);
}

/** For pages that show their own "not found" (a match, a player, a tournament …). */
export function isNotFound(err: unknown): boolean {
  return err instanceof ApiError && (err.status === 404 || err.code === "VALIDATION_FAILED" || err.code === "BAD_PATH");
}

/**
 * A request was refused because the session is gone (expired, revoked, logged out in another tab): send the user to
 * the login page. Not for the "who am I" query itself – that one answering 401 is simply how "logged out" is known.
 */
export function shouldExpireSession(err: unknown, queryKey: readonly unknown[] | undefined): boolean {
  if (!(err instanceof ApiError) || err.status !== 401 || err.code === "AUTH_INVALID_CREDENTIALS") return false;
  return !(queryKey && queryKey.length === 1 && queryKey[0] === "me");
}
