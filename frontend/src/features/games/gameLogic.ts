import type { GameType, GameView, PlayerSlot, PlayerSummary } from "../../api/types";

/** Mirrors the server's lineup rules (PartyRules) so the form can say what's wrong before sending. */
export type LineupProblem = "tooFew" | "tooMany" | "teamsNeedFour" | "noTeams" | "x01Two";

export function lineupError(type: GameType, teams: boolean, count: number): LineupProblem | null {
  if (teams) {
    if (type === "KILLER" || type === "AROUND_THE_CLOCK") return "noTeams";
    return count === 4 ? null : "teamsNeedFour";
  }
  const [min, max] = { X01: [3, 4], CRICKET: [2, 4], KILLER: [2, 8], AROUND_THE_CLOCK: [1, 8] }[type];
  if (type === "X01" && count === 2) return "x01Two"; // two players: a normal (rated) match
  if (count < min) return "tooFew";
  if (count > max) return "tooMany";
  return null;
}

/** Doubles go to the server as [A1, A2, B1, B2]; it makes the throwing order A1, B1, A2, B2. */
export function playerIdsForRequest(teams: boolean, first: number[], second: number[]): number[] {
  return teams ? [...first, ...second] : [...first];
}

type Lineup = Pick<GameView, "teams" | "players">;

export function slotsOfSide(game: Lineup, side: number): number[] {
  return game.players.filter((p) => p.side === side).map((p) => p.slot);
}

export function sideMembers(game: Lineup, side: number): PlayerSummary[] {
  return game.players.filter((p: PlayerSlot) => p.side === side).map((p) => p.player);
}

export function sideLabel(game: Lineup, side: number): string {
  return sideMembers(game, side).map((p) => p.realName).join(" & ");
}

export const CRICKET_TARGETS = [20, 19, 18, 17, 16, 15, 25];

export function markSymbol(marks: number): string {
  return marks <= 0 ? "" : marks === 1 ? "/" : marks === 2 ? "X" : "⊗";
}

/** Around the Clock: 1 to 20, then the bull; null once finished. */
export function clockTargetLabel(target: number | null): string {
  if (target === null) return "✓";
  return target === 25 ? "BULL" : String(target);
}
