// Client-side helpers for party games: who can play what, how a doubles lineup is sent, and what the boards show.
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { CRICKET_TARGETS, lineupError, markLevel, playerIdsForRequest, sideLabel, sideMembers, slotsOfSide, clockTargetLabel } from "./gameLogic.ts";
import type { GameView, PlayerSlot } from "../../api/types";

const p = (id: number, name = `P${id}`) => ({ id, username: `u${id}`, realName: name, rating: 1500, rd: 100, provisional: false });

describe("who can play", () => {
  it("follows the server's lineup rules", () => {
    assert.equal(lineupError("X01", false, 2), "x01Two");
    assert.equal(lineupError("X01", false, 3), null);
    assert.equal(lineupError("X01", false, 4), null);
    assert.equal(lineupError("X01", false, 5), "tooMany");
    assert.equal(lineupError("X01", true, 4), null);
    assert.equal(lineupError("X01", true, 3), "teamsNeedFour");
    assert.equal(lineupError("CRICKET", false, 1), "tooFew");
    assert.equal(lineupError("CRICKET", false, 2), null);
    assert.equal(lineupError("CRICKET", false, 4), null);
    assert.equal(lineupError("CRICKET", false, 5), "tooMany");
    assert.equal(lineupError("CRICKET", true, 4), null);
    assert.equal(lineupError("KILLER", false, 2), null);
    assert.equal(lineupError("KILLER", false, 8), null);
    assert.equal(lineupError("KILLER", false, 9), "tooMany");
    assert.equal(lineupError("KILLER", true, 4), "noTeams");
    assert.equal(lineupError("AROUND_THE_CLOCK", false, 1), null);
    assert.equal(lineupError("AROUND_THE_CLOCK", false, 0), "tooFew");
    assert.equal(lineupError("AROUND_THE_CLOCK", true, 4), "noTeams");
  });
});

describe("doubles", () => {
  it("are sent as team A then team B; the server alternates them", () => {
    assert.deepEqual(playerIdsForRequest(true, [1, 2], [3, 4]), [1, 2, 3, 4]);
  });
  it("individual games are sent in throwing order", () => {
    assert.deepEqual(playerIdsForRequest(false, [5, 1, 9], []), [5, 1, 9]);
  });
});

describe("sides", () => {
  const players: PlayerSlot[] = [
    { slot: 0, side: 0, player: p(1, "Ann") },
    { slot: 1, side: 1, player: p(3, "Cat") },
    { slot: 2, side: 0, player: p(2, "Bob") },
    { slot: 3, side: 1, player: p(4, "Dan") },
  ];
  const team = { teams: true, players } as Pick<GameView, "teams" | "players">;
  it("group a team's players", () => {
    assert.deepEqual(sideMembers(team, 0).map((x) => x.realName), ["Ann", "Bob"]);
    assert.deepEqual(slotsOfSide(team, 1), [1, 3]);
  });
  it("are named after their players", () => {
    assert.equal(sideLabel(team, 0), "Ann & Bob");
    const solo = { teams: false, players: [{ slot: 0, side: 0, player: p(7, "Eve") }] };
    assert.equal(sideLabel(solo, 0), "Eve");
  });
});

describe("board symbols", () => {
  it("cricket marks: none, a slash, a cross, a closed circle (drawn as SVG, never as a symbol character)", () => {
    assert.deepEqual([0, 1, 2, 3].map(markLevel), [0, 1, 2, 3]);
    assert.equal(markLevel(5), 3);
    assert.equal(markLevel(-1), 0);
  });
  it("cricket is played on 20 down to 15 and the bull", () => {
    assert.deepEqual(CRICKET_TARGETS, [20, 19, 18, 17, 16, 15, 25]);
  });
  it("around the clock ends on the bull", () => {
    assert.equal(clockTargetLabel(1), "1");
    assert.equal(clockTargetLabel(20), "20");
    assert.equal(clockTargetLabel(25), "Bull");
    assert.equal(clockTargetLabel(null), "", "finished is shown with an icon");
  });
});
