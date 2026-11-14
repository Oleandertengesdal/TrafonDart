import { ApiError } from "../../api/client";

/**
 * The pure parts of the offline dart queue (the hook is useDartQueue).
 *
 * Every dart is sent with the dart count the client expects, so the server refuses anything that would record a dart
 * twice. That makes resending safe: darts typed while the connection is down wait here, survive a reload in
 * localStorage, and are sent in order when the connection comes back.
 */
export interface QueuedDart {
  segment: number;
  multiplier: number;
}

/** A whole leg or round can be typed offline; more than that is surely a mistake. */
export const MAX_QUEUED = 30;

export function dartLabelOf(d: QueuedDart): string {
  if (d.segment === 0) return "MISS";
  if (d.segment === 25) return d.multiplier === 2 ? "BULL" : "25";
  return (d.multiplier === 1 ? "S" : d.multiplier === 2 ? "D" : "T") + d.segment;
}

export function isValidDart(d: unknown): d is QueuedDart {
  if (!d || typeof d !== "object") return false;
  const { segment, multiplier } = d as Record<string, unknown>;
  if (!Number.isInteger(segment) || !Number.isInteger(multiplier)) return false;
  const s = segment as number;
  const m = multiplier as number;
  if (s === 0) return m === 0; // a miss: segment 0, multiplier 0 (as the keypad sends it)
  if (s === 25) return m === 1 || m === 2;
  return s >= 1 && s <= 20 && m >= 1 && m <= 3;
}

export type Outcome = "retry" | "out-of-sync" | "give-up";

/** Keep trying on network trouble and server errors; let the server win on a conflict; stop on anything else. */
export function classify(err: unknown): Outcome {
  if (!(err instanceof ApiError)) return "give-up";
  if (err.code === "MATCH_OUT_OF_SYNC") return "out-of-sync";
  if (err.code === "NETWORK_ERROR" || err.status === 0 || err.status >= 500 || err.status === 429) return "retry";
  return "give-up";
}

export function backoffMs(attempt: number): number {
  return Math.min(1000 * 2 ** Math.min(attempt, 10), 15_000);
}

/**
 * After a retry is refused as out of sync: did our earlier attempt actually land (only the answer got lost)? True when
 * the server is exactly one dart past what we expected and that dart is ours (or the server doesn't say which it was).
 */
export function wasApplied(expected: number, dart: QueuedDart, server: { dartCount: number }, serverLastLabel: string | null): boolean {
  if (server.dartCount !== expected + 1) return false;
  return serverLastLabel === null || serverLastLabel === dartLabelOf(dart);
}

export function storageKey(kind: "match" | "game" | "bot", id: string): string {
  return `td.pending.${kind}.${id}`;
}

export interface Persisted {
  base: number;
  darts: QueuedDart[];
}

/** {@code base} is the dart count the first queued dart expects. */
export function serialize(base: number, darts: QueuedDart[]): string {
  return JSON.stringify({ v: 1, base, darts });
}

export function parsePersisted(raw: string | null): Persisted | null {
  if (!raw) return null;
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!data || typeof data !== "object" || Array.isArray(data)) return null;
  const { v, base, darts } = data as Record<string, unknown>;
  if (v !== 1 || !Number.isInteger(base) || (base as number) < 0) return null;
  if (!Array.isArray(darts) || darts.length === 0 || darts.length > MAX_QUEUED || !darts.every(isValidDart)) return null;
  return { base: base as number, darts: darts.map((d) => ({ segment: d.segment, multiplier: d.multiplier })) };
}

/**
 * Which saved darts still need sending, given the server's dart count now: the ones it hasn't seen yet. Null when the
 * board no longer fits the saved queue (someone else scored or undid in the meantime) – then the queue is dropped.
 */
export function recover(saved: Persisted, serverDartCount: number): QueuedDart[] | null {
  const applied = serverDartCount - saved.base;
  if (applied < 0 || applied > saved.darts.length) return null;
  return saved.darts.slice(applied);
}
