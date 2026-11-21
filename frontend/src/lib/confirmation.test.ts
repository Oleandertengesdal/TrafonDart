import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type { ConfirmationView } from "../api/types.ts";
import { confirmationState, isFinishedScore, resultDateRange } from "./confirmation.ts";

const players = [
  { id: 1, realName: "Kari" },
  { id: 2, realName: "Ola" },
] as const;

const waitingForOla: ConfirmationView = {
  registeredBy: "kari",
  confirmerIds: [2],
  confirmedAt: null,
  confirmedBy: null,
  autoConfirmAt: "2026-11-23T20:00:00Z",
};

function match(over: Partial<Parameters<typeof confirmationState>[0]> = {}): Parameters<typeof confirmationState>[0] {
  return { status: "UNCONFIRMED", disputed: false, players, confirmation: waitingForOla, ...over };
}

const kari = { id: 1, role: "USER" as const };
const ola = { id: 2, role: "USER" as const };
const outsider = { id: 9, role: "USER" as const };
const admin = { id: 7, role: "ADMIN" as const };

describe("confirmationState: who sees Confirm and Dispute on a finished match", () => {
  it("waits for the player who didn't register it, and only they (or an admin) can confirm", () => {
    const s = confirmationState(match(), ola);
    assert.equal(s.waiting, true);
    assert.deepEqual(s.waitingFor, ["Ola"]);
    assert.equal(s.canConfirm, true);
    assert.equal(confirmationState(match(), kari).canConfirm, false, "not your own registration");
    assert.equal(confirmationState(match(), outsider).canConfirm, false);
    assert.equal(confirmationState(match(), admin).canConfirm, true, "an admin always can");
    assert.equal(confirmationState(match(), null).canConfirm, false);
  });

  it("a disputed match is for the admins: players can't confirm or dispute it again", () => {
    const disputed = match({ disputed: true });
    assert.equal(confirmationState(disputed, ola).canConfirm, false);
    assert.equal(confirmationState(disputed, ola).canDispute, false);
    assert.equal(confirmationState(disputed, admin).canConfirm, true);
  });

  it("both players can dispute while it waits, and after it counts; outsiders can't", () => {
    assert.equal(confirmationState(match(), kari).canDispute, true);
    assert.equal(confirmationState(match(), ola).canDispute, true);
    assert.equal(confirmationState(match(), outsider).canDispute, false);
    const counted = match({ status: "COMPLETED", confirmation: { ...waitingForOla, confirmedAt: "2026-11-21T10:00:00Z", confirmedBy: "ola", autoConfirmAt: null } });
    const s = confirmationState(counted, kari);
    assert.equal(s.waiting, false);
    assert.equal(s.canConfirm, false);
    assert.equal(s.canDispute, true);
  });

  it("tournament matches and matches being played have nothing to confirm", () => {
    assert.equal(confirmationState(match({ status: "COMPLETED", confirmation: null }), admin).canConfirm, false);
    assert.equal(confirmationState(match({ status: "IN_PROGRESS", confirmation: null }), ola).waiting, false);
    assert.equal(confirmationState(match({ status: "IN_PROGRESS", confirmation: null }), ola).canDispute, false);
  });
});

describe("isFinishedScore: a registered result must be a finished match", () => {
  it("exactly one player has the legs needed to win", () => {
    assert.equal(isFinishedScore(2, [2, 1]), true);
    assert.equal(isFinishedScore(2, [0, 2]), true);
    assert.equal(isFinishedScore(1, [1, 0]), true);
    assert.equal(isFinishedScore(2, [2, 2]), false);
    assert.equal(isFinishedScore(2, [1, 0]), false);
    assert.equal(isFinishedScore(2, [3, 0]), false);
    assert.equal(isFinishedScore(2, [-1, 2]), false);
    assert.equal(isFinishedScore(2, [1.5, 2]), false);
  });
});

describe("resultDateRange: today and up to 7 days back, in Norwegian time", () => {
  it("counts days in Oslo, not UTC", () => {
    // 23:30 UTC on 21 Nov is already 22 Nov in Oslo
    assert.deepEqual(resultDateRange(new Date("2026-11-21T23:30:00Z")), { min: "2026-11-15", max: "2026-11-22" });
    assert.deepEqual(resultDateRange(new Date("2026-03-03T10:00:00Z")), { min: "2026-02-24", max: "2026-03-03" });
  });
});
