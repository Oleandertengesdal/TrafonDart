import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, api } from "../../api/client";
import {
  MAX_QUEUED,
  backoffMs,
  classify,
  parsePersisted,
  recover,
  serialize,
  storageKey,
  wasApplied,
  type QueuedDart,
} from "./dartQueue";

interface Scoreable {
  dartCount: number;
  status: string;
}

export interface DartQueueOptions<S extends Scoreable> {
  kind: "match" | "game" | "bot";
  id: string;
  queryKey: readonly unknown[];
  throwPath: string;
  undoPath: string;
  /** The label of the last dart on the board ("T20", "MISS"…), or null if the state doesn't say. */
  lastLabel: (state: S) => string | null;
}

function storage(): Storage | null {
  try {
    return typeof localStorage === "undefined" ? null : localStorage;
  } catch {
    return null;
  }
}

/**
 * Sends darts one at a time, in order, while the scorer keeps tapping – and keeps them when the connection drops.
 *
 * Each request carries the dart count the client expects, so a double tap, a retry or a second tablet can never record
 * a dart twice. Darts tapped while a request is in flight wait in the queue ("pending" in the turn strip). When the
 * network or server is down, the queue is kept in localStorage and resent with backoff (and at once when the browser
 * says it is online again); up to {@link MAX_QUEUED} darts can wait. If a retry is refused because the earlier attempt
 * did land (only its answer was lost), the dart counts as sent. Any other conflict: the server's state wins.
 */
export function useDartQueue<S extends Scoreable>(opts: DartQueueOptions<S>) {
  const { kind, id, queryKey, throwPath, undoPath, lastLabel } = opts;
  const qc = useQueryClient();
  const queue = useRef<QueuedDart[]>([]);
  const sending = useRef(false);
  const uncertain = useRef(false); // the first queued dart may already have landed (its answer got lost)
  const attempt = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const restored = useRef(false);
  const [pending, setPending] = useState<QueuedDart[]>([]);
  const [offline, setOffline] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const key = storageKey(kind, id);
  const labelRef = useRef(lastLabel);
  useEffect(() => {
    labelRef.current = lastLabel;
  });

  const current = useCallback(() => qc.getQueryData<S>(queryKey), [qc, queryKey]);
  const store = useCallback((state: S) => qc.setQueryData(queryKey, state), [qc, queryKey]);

  const persist = useCallback(() => {
    const s = storage();
    if (!s) return;
    const state = current();
    try {
      if (queue.current.length === 0 || !state) s.removeItem(key);
      else s.setItem(key, serialize(state.dartCount, queue.current));
    } catch {
      // storage full or blocked: the queue still lives in memory
    }
  }, [current, key]);

  const sync = useCallback(() => {
    setPending([...queue.current]);
    persist();
  }, [persist]);

  const pump = useCallback(async () => {
    if (sending.current) return;
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    sending.current = true;
    try {
      while (queue.current.length > 0) {
        const state = current();
        if (!state || state.status !== "IN_PROGRESS") {
          queue.current = [];
          break;
        }
        const dart = queue.current[0];
        const expected = state.dartCount;
        try {
          const next = await api.post<S>(throwPath, { segment: dart.segment, multiplier: dart.multiplier, expectedDartCount: expected });
          store(next);
          queue.current.shift();
          uncertain.current = false;
          attempt.current = 0;
          setOffline(false);
        } catch (err) {
          const outcome = classify(err);
          if (outcome === "retry") {
            uncertain.current = true;
            setOffline(true);
            timer.current = setTimeout(() => void pump(), backoffMs(attempt.current++));
            break;
          }
          const server = err instanceof ApiError && err.code === "MATCH_OUT_OF_SYNC" ? (err.details as S | undefined) : undefined;
          if (outcome === "out-of-sync" && server && uncertain.current && wasApplied(expected, dart, server, labelRef.current(server))) {
            // our earlier attempt landed; carry on with the next dart
            store(server);
            queue.current.shift();
            uncertain.current = false;
            setOffline(false);
            continue;
          }
          queue.current = [];
          uncertain.current = false;
          setOffline(false);
          if (server) store(server);
          else void qc.invalidateQueries({ queryKey });
          setError(err);
        }
        sync();
      }
    } finally {
      sending.current = false;
      sync();
    }
  }, [current, store, sync, throwPath, qc, queryKey]);

  // After a reload: pick up darts saved while offline, minus any the server already has.
  const state = qc.getQueryData<S>(queryKey);
  useEffect(() => {
    if (restored.current || !state) return;
    restored.current = true;
    const s = storage();
    const saved = parsePersisted(s?.getItem(key) ?? null);
    if (!saved) return;
    const rest = state.status === "IN_PROGRESS" ? recover(saved, state.dartCount) : null;
    if (rest && rest.length > 0) {
      queue.current = rest;
      setPending([...rest]);
      void pump();
    } else {
      s?.removeItem(key);
    }
  }, [state, key, pump]);

  useEffect(() => {
    const online = () => {
      attempt.current = 0;
      void pump();
    };
    window.addEventListener("online", online);
    return () => {
      window.removeEventListener("online", online);
      if (timer.current) clearTimeout(timer.current);
    };
  }, [pump]);

  const throwDart = useCallback(
    (segment: number, multiplier: number) => {
      setError(null);
      // Online, the queue never holds more than a turn; offline, a whole leg can wait.
      const cap = offline ? MAX_QUEUED : 3;
      if (queue.current.length >= cap) return;
      queue.current.push({ segment, multiplier });
      sync();
      void pump();
    },
    [pump, sync, offline],
  );

  const undo = useCallback(async () => {
    setError(null);
    // A dart that hasn't been sent yet is simply dropped.
    if (queue.current.length > (sending.current ? 1 : 0) || (offline && queue.current.length > 0 && !sending.current)) {
      queue.current.pop();
      sync();
      return;
    }
    if (sending.current) return;
    const s = current();
    if (!s || s.dartCount === 0) return;
    try {
      store(await api.post<S>(undoPath, { expectedDartCount: s.dartCount }));
    } catch (err) {
      if (err instanceof ApiError && err.code === "MATCH_OUT_OF_SYNC" && err.details) store(err.details as S);
      setError(err);
    }
  }, [current, store, sync, undoPath, offline]);

  /** True while our own darts are on their way – socket pushes are ignored then to avoid flicker. */
  const busy = useCallback(() => sending.current || queue.current.length > 0, []);

  return { pending, offline, throwDart, undo, error, clearError: () => setError(null), busy };
}
