import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { BOT_LEVELS, asLiveMatch, botDartsToReveal, nextLevel, type BotMatchLike } from "./botLogic.ts";

const turn = (labels: string[], total: number) => ({
  playerIndex: 1,
  darts: labels.map((label) => ({ segment: 20, multiplier: 1, label, points: 20 })),
  total,
  bust: false,
  checkout: false,
});

function match(over: Partial<BotMatchLike>): BotMatchLike {
  return { status: "IN_PROGRESS", dartCount: 0, botTurns: [], ...over };
}

describe("Trafo's levels", () => {
  it("go from beginner to pro", () => {
    assert.deepEqual([...BOT_LEVELS], ["BEGINNER", "CASUAL", "CLUB", "LEAGUE", "PRO"]);
  });

  it("the next level up, and none above pro", () => {
    assert.equal(nextLevel("BEGINNER"), "CASUAL");
    assert.equal(nextLevel("LEAGUE"), "PRO");
    assert.equal(nextLevel("PRO"), null);
  });
});

describe("showing Trafo's darts", () => {
  const answer = match({ dartCount: 6, botTurns: [turn(["T20", "S20", "S5"], 85)] });

  it("shows its answer when your dart brought it on", () => {
    assert.deepEqual(botDartsToReveal(3, answer)?.map((t) => t.total), [85]);
  });

  it("shows nothing for your own dart mid-turn, or after an undo", () => {
    assert.equal(botDartsToReveal(3, match({ dartCount: 4 })), null);
    assert.equal(botDartsToReveal(6, match({ dartCount: 2 })), null);
    assert.equal(botDartsToReveal(6, answer), null);
  });

  it("on opening a match, shows Trafo's first turn only if nothing else has been thrown", () => {
    assert.equal(botDartsToReveal(null, match({ dartCount: 3, botTurns: [turn(["S20", "S1", "S5"], 26)] }))?.length, 1);
    assert.equal(botDartsToReveal(null, answer), null);
  });
});

describe("the scoreboard", () => {
  it("shares the match components: a match given up reads as abandoned", () => {
    const m = { status: "FORFEITED", dartCount: 4, botTurns: [], level: "PRO", typicalAverage: 88 } as unknown as Parameters<typeof asLiveMatch>[0];
    assert.equal(asLiveMatch(m).status, "ABANDONED");
    assert.equal(asLiveMatch({ ...m, status: "COMPLETED" }).status, "COMPLETED");
  });
});
