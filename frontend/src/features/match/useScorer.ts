import { useMemo } from "react";
import { apiPath } from "../../api/client";
import { keys } from "../../api/queries";
import type { LiveMatch } from "../../api/types";
import { useDartQueue } from "../scoring/useDartQueue";

/** The last dart on a match board, for recognising a dart whose answer got lost. */
export function lastMatchDart(m: LiveMatch): string | null {
  const turn = m.currentTurn && m.currentTurn.darts.length > 0 ? m.currentTurn : m.turnJustEnded ? m.lastCompletedTurn : null;
  const darts = turn?.darts ?? [];
  return darts.length > 0 ? darts[darts.length - 1].label : null;
}

/** An API path, or "" (which the client refuses as BAD_PATH) for an id like ".." from the address bar. */
export function pathOrEmpty(build: () => string): string {
  try {
    return build();
  } catch {
    return "";
  }
}

/** Scoring a 1v1 match: darts are queued, sent in order, and kept while the connection is down. */
export function useScorer(matchId: string) {
  const queryKey = useMemo(() => keys.match(matchId), [matchId]);
  return useDartQueue<LiveMatch>({
    kind: "match",
    id: matchId,
    queryKey,
    throwPath: pathOrEmpty(() => apiPath`/api/matches/${matchId}/throws`),
    undoPath: pathOrEmpty(() => apiPath`/api/matches/${matchId}/undo`),
    lastLabel: lastMatchDart,
  });
}
