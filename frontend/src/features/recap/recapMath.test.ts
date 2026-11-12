// Fun facts and the year picker for the year recap.
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { recapPromo, recapYears, walkedKm, winRate } from "./recapMath.ts";

describe("year recap facts", () => {
  it("turns darts into kilometres walked to the board", () => {
    assert.equal(walkedKm(0), 0);
    assert.equal(walkedKm(3), 0); // 4.74 m
    assert.equal(walkedKm(3000), 4.7);
    assert.equal(walkedKm(9000), 14.2);
  });

  it("never divides by zero", () => {
    assert.equal(winRate(0, 0), 0);
    assert.equal(winRate(3, 4), 0.75);
  });

  it("offers every year since joining, newest first", () => {
    const now = new Date("2026-11-12T12:00:00Z");
    assert.deepEqual(recapYears("2024-03-01T00:00:00Z", now), [2026, 2025, 2024]);
    assert.deepEqual(recapYears(null, now), [2026]);
    assert.deepEqual(recapYears("2030-01-01T00:00:00Z", now), [2026]);
  });

  it("is promoted in December (this year) and January (last year) only", () => {
    assert.deepEqual(recapPromo(new Date(2026, 11, 3)), { year: 2026 });
    assert.deepEqual(recapPromo(new Date(2027, 0, 20)), { year: 2026 });
    assert.equal(recapPromo(new Date(2026, 10, 12)), null);
    assert.equal(recapPromo(new Date(2026, 1, 1)), null);
  });
});
