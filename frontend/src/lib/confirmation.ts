import type { ConfirmationView, MatchStatus, Role } from "../api/types";

/**
 * A finished normal match counts only once it's confirmed: by the player who didn't register it, by an admin, or by
 * itself after two days. The server decides; this mirrors its rules so the right buttons show.
 */
export interface ConfirmationState {
  /** Finished, not counted yet. */
  waiting: boolean;
  /** Real names of the players whose confirmation it waits for. */
  waitingFor: string[];
  canConfirm: boolean;
  canDispute: boolean;
}

interface MatchLike {
  status: MatchStatus;
  disputed: boolean;
  players: readonly { id: number; realName: string }[];
  confirmation: ConfirmationView | null;
}

export function confirmationState(match: MatchLike, me: { id: number; role: Role } | null): ConfirmationState {
  const waiting = match.status === "UNCONFIRMED";
  const ids = match.confirmation?.confirmerIds ?? [];
  const isAdmin = me?.role === "ADMIN";
  const isPlayer = !!me && match.players.some((p) => p.id === me.id);
  return {
    waiting,
    waitingFor: waiting ? match.players.filter((p) => ids.includes(p.id)).map((p) => p.realName) : [],
    // a disputed match is the admins' to decide; an admin confirming it settles the dispute
    canConfirm: waiting && !!me && (isAdmin || (!match.disputed && ids.includes(me.id))),
    canDispute: (waiting || match.status === "COMPLETED") && isPlayer && !match.disputed,
  };
}

/** A registered result must be a finished match: exactly one player has the legs needed to win. */
export function isFinishedScore(legsToWin: number, legs: readonly [number, number]): boolean {
  const ok = (n: number) => Number.isInteger(n) && n >= 0 && n <= legsToWin;
  if (!ok(legs[0]) || !ok(legs[1])) return false;
  return (legs[0] === legsToWin) !== (legs[1] === legsToWin);
}

/** How far back a result can be registered (the server checks the same). */
export const RESULT_MAX_DAYS_BACK = 7;

/** Today and the earliest allowed day, as YYYY-MM-DD in Norwegian time: the limits for the date field. */
export function resultDateRange(now: Date = new Date()): { min: string; max: string } {
  const day = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Oslo" }).format(d);
  const max = day(now);
  const [y, m, d] = max.split("-").map(Number);
  const earliest = new Date(Date.UTC(y, m - 1, d - RESULT_MAX_DAYS_BACK, 12));
  return { min: earliest.toISOString().slice(0, 10), max };
}
