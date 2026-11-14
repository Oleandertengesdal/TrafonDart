import type { BotLevel, BotMatch, LiveMatch, TurnView } from "../../api/types";

/** Trafo's levels, easiest first (the server's BotLevel). */
export const BOT_LEVELS = ["BEGINNER", "CASUAL", "CLUB", "LEAGUE", "PRO"] as const satisfies readonly BotLevel[];

/** The 3-dart average each level plays at (the server's BotLevel.typicalAverage, checked by simulation there). */
export const BOT_AVERAGES: Record<BotLevel, number> = { BEGINNER: 30, CASUAL: 42, CLUB: 55, LEAGUE: 70, PRO: 88 };

export function nextLevel(level: BotLevel): BotLevel | null {
  const i = BOT_LEVELS.indexOf(level);
  return i >= 0 && i < BOT_LEVELS.length - 1 ? BOT_LEVELS[i + 1] : null;
}

export type BotMatchLike = Pick<BotMatch, "status" | "dartCount" | "botTurns">;

/**
 * Trafo's turns to show dart by dart, or null. They are shown when your dart brought Trafo on (the dart count went
 * up and Trafo has thrown since your last dart), and when a match is opened in which only Trafo has thrown so far.
 */
export function botDartsToReveal(previousCount: number | null, next: BotMatchLike): TurnView[] | null {
  if (next.botTurns.length === 0) return null;
  if (previousCount === null) {
    const trafosDarts = next.botTurns.reduce((n, t) => n + t.darts.length, 0);
    return trafosDarts === next.dartCount ? next.botTurns : null;
  }
  return next.dartCount > previousCount ? next.botTurns : null;
}

/** The match components show a bot match as a match; one that was given up reads as abandoned. */
export function asLiveMatch(m: BotMatch): LiveMatch {
  return { ...m, status: m.status === "FORFEITED" ? "ABANDONED" : m.status };
}
