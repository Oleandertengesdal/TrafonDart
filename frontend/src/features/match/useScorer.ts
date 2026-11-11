import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";
import { ApiError, api } from "../../api/client";
import { keys } from "../../api/queries";
import type { LiveMatch } from "../../api/types";
import type { PendingDart } from "./TurnStrip";

/**
 * Sends darts one at a time, in order, while letting the scorer keep tapping.
 *
 * Each request carries the dart count the client expects, so a double tap or a second tablet can never
 * record a dart twice. Darts tapped while a request is in flight wait in a small queue and are shown as
 * "pending" in the turn strip. On MATCH_OUT_OF_SYNC the server's state wins and the queue is dropped.
 */
export function useScorer(matchId: string) {
  const qc = useQueryClient();
  const queue = useRef<PendingDart[]>([]);
  const sending = useRef(false);
  const [pending, setPending] = useState<PendingDart[]>([]);
  const [error, setError] = useState<unknown>(null);

  const current = () => qc.getQueryData<LiveMatch>(keys.match(matchId));
  const store = (state: LiveMatch) => qc.setQueryData(keys.match(matchId), state);

  const pump = useCallback(async () => {
    if (sending.current) return;
    sending.current = true;
    try {
      while (queue.current.length > 0) {
        const state = current();
        if (!state || state.status !== "IN_PROGRESS") {
          queue.current = [];
          break;
        }
        const dart = queue.current[0];
        try {
          const next = await api.post<LiveMatch>(`/api/matches/${matchId}/throws`, {
            segment: dart.segment,
            multiplier: dart.multiplier,
            expectedDartCount: state.dartCount,
          });
          store(next);
          queue.current.shift();
        } catch (err) {
          queue.current = [];
          if (err instanceof ApiError && err.code === "MATCH_OUT_OF_SYNC" && err.details) {
            store(err.details as LiveMatch);
          } else {
            void qc.invalidateQueries({ queryKey: keys.match(matchId) });
          }
          setError(err);
        }
        setPending([...queue.current]);
      }
    } finally {
      sending.current = false;
      setPending([...queue.current]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matchId]);

  const throwDart = useCallback(
    (segment: number, multiplier: number) => {
      setError(null);
      // Never queue more darts than the turn can hold beyond what is already being sent.
      if (queue.current.length >= 3) return;
      queue.current.push({ segment, multiplier });
      setPending([...queue.current]);
      void pump();
    },
    [pump],
  );

  const undo = useCallback(async () => {
    setError(null);
    // Undo a dart that hasn't been sent yet: just drop it.
    if (queue.current.length > (sending.current ? 1 : 0)) {
      queue.current.pop();
      setPending([...queue.current]);
      return;
    }
    if (sending.current) return;
    const state = current();
    if (!state || state.dartCount === 0) return;
    try {
      store(await api.post<LiveMatch>(`/api/matches/${matchId}/undo`, { expectedDartCount: state.dartCount }));
    } catch (err) {
      if (err instanceof ApiError && err.code === "MATCH_OUT_OF_SYNC" && err.details) store(err.details as LiveMatch);
      setError(err);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matchId]);

  /** True while our own requests are in flight – socket pushes are ignored then to avoid flicker. */
  const busy = () => sending.current || queue.current.length > 0;

  return { pending, throwDart, undo, error, clearError: () => setError(null), busy };
}
