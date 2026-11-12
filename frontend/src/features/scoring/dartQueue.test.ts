// The offline dart queue: darts typed while the connection is down are kept (also across a reload) and sent in
// order when it comes back, without ever recording a dart twice.
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import {
  MAX_QUEUED,
  backoffMs,
  classify,
  dartLabelOf,
  isValidDart,
  parsePersisted,
  recover,
  serialize,
  storageKey,
  wasApplied,
} from "./dartQueue.ts";
import { ApiError } from "../../api/client.ts";

const err = (code: string, status: number, details?: unknown) => new ApiError({ code, status, details });

describe("dart labels", () => {
  it("match the server's labels", () => {
    assert.equal(dartLabelOf({ segment: 0, multiplier: 0 }), "MISS");
    assert.equal(dartLabelOf({ segment: 25, multiplier: 1 }), "25");
    assert.equal(dartLabelOf({ segment: 25, multiplier: 2 }), "BULL");
    assert.equal(dartLabelOf({ segment: 20, multiplier: 1 }), "S20");
    assert.equal(dartLabelOf({ segment: 16, multiplier: 2 }), "D16");
    assert.equal(dartLabelOf({ segment: 19, multiplier: 3 }), "T19");
  });

  it("only real darts are valid", () => {
    // a miss is segment 0, multiplier 0 – exactly what the keypad sends and the server stores
    for (const ok of [{ segment: 0, multiplier: 0 }, { segment: 20, multiplier: 3 }, { segment: 25, multiplier: 2 }, { segment: 1, multiplier: 1 }]) {
      assert.ok(isValidDart(ok), JSON.stringify(ok));
    }
    for (const bad of [
      { segment: 25, multiplier: 3 },
      { segment: 21, multiplier: 1 },
      { segment: -1, multiplier: 1 },
      { segment: 5, multiplier: 0 },
      { segment: 5, multiplier: 4 },
      { segment: 0, multiplier: 2 },
      { segment: 0, multiplier: 1 },
      { segment: 20, multiplier: 0 },
      { segment: 1.5, multiplier: 1 },
      { segment: "20", multiplier: 1 },
      null,
      {},
    ]) {
      assert.ok(!isValidDart(bad), JSON.stringify(bad));
    }
  });
});

describe("what to do when a send fails", () => {
  it("keeps the queue when the connection or server is down", () => {
    assert.equal(classify(err("NETWORK_ERROR", 0)), "retry");
    assert.equal(classify(err("INTERNAL_ERROR", 500)), "retry");
    assert.equal(classify(err("UNKNOWN", 502)), "retry");
    assert.equal(classify(err("UNKNOWN", 503)), "retry");
    assert.equal(classify(err("RATE_LIMITED", 429)), "retry");
  });

  it("lets the server win on a conflict", () => {
    assert.equal(classify(err("MATCH_OUT_OF_SYNC", 409, { dartCount: 3 })), "out-of-sync");
  });

  it("gives up on anything that won't get better", () => {
    for (const e of [err("MATCH_NOT_SCORER", 403), err("MATCH_NOT_IN_PROGRESS", 409), err("VALIDATION_FAILED", 400), err("AUTH_REQUIRED", 401), new Error("x")]) {
      assert.equal(classify(e), "give-up", String(e));
    }
  });

  it("backs off, but never longer than 15 s", () => {
    assert.equal(backoffMs(0), 1000);
    assert.equal(backoffMs(1), 2000);
    assert.equal(backoffMs(3), 8000);
    assert.equal(backoffMs(10), 15000);
    assert.equal(backoffMs(100), 15000);
  });
});

describe("a response that got lost", () => {
  // We sent a dart expecting the board to be at 10. The answer never arrived; the retry is refused as out of sync.
  const d = { segment: 20, multiplier: 3 };
  it("counts as sent when the server is exactly one dart ahead and that dart is ours", () => {
    assert.equal(wasApplied(10, d, { dartCount: 11 }, "T20"), true);
  });
  it("counts as sent when the server can't tell us the last dart", () => {
    assert.equal(wasApplied(10, d, { dartCount: 11 }, null), true);
  });
  it("is a real conflict when the last dart is someone else's", () => {
    assert.equal(wasApplied(10, d, { dartCount: 11 }, "S5"), false);
  });
  it("is a real conflict when the server moved on further or went back", () => {
    assert.equal(wasApplied(10, d, { dartCount: 12 }, "T20"), false);
    assert.equal(wasApplied(10, d, { dartCount: 10 }, "T20"), false);
    assert.equal(wasApplied(10, d, { dartCount: 9 }, null), false);
  });
});

describe("persisting across a reload", () => {
  const darts = [
    { segment: 20, multiplier: 1 },
    { segment: 20, multiplier: 3 },
    { segment: 5, multiplier: 1 },
  ];

  it("uses a key per match or game", () => {
    assert.equal(storageKey("match", "abc"), "td.pending.match.abc");
    assert.equal(storageKey("game", "abc"), "td.pending.game.abc");
    assert.notEqual(storageKey("match", "a"), storageKey("game", "a"));
  });

  it("round-trips, misses included", () => {
    const back = parsePersisted(serialize(7, darts));
    assert.deepEqual(back, { base: 7, darts });
    const withMiss = [...darts, { segment: 0, multiplier: 0 }];
    assert.deepEqual(parsePersisted(serialize(7, withMiss)), { base: 7, darts: withMiss });
  });

  it("ignores anything broken or tampered with", () => {
    for (const raw of [
      null,
      "",
      "not json",
      "[]",
      JSON.stringify({ v: 2, base: 1, darts }),
      JSON.stringify({ v: 1, base: -1, darts }),
      JSON.stringify({ v: 1, base: 1.5, darts }),
      JSON.stringify({ v: 1, base: 1, darts: "x" }),
      JSON.stringify({ v: 1, base: 1, darts: [{ segment: 25, multiplier: 3 }] }),
      JSON.stringify({ v: 1, base: 1, darts: Array.from({ length: MAX_QUEUED + 1 }, () => darts[0]) }),
      JSON.stringify({ v: 1, base: 1, darts: [] }),
    ]) {
      assert.equal(parsePersisted(raw), null, String(raw));
    }
  });

  it("resends everything when the server never saw any of it", () => {
    assert.deepEqual(recover({ base: 7, darts }, 7), darts);
  });

  it("skips the darts the server already has", () => {
    assert.deepEqual(recover({ base: 7, darts }, 9), darts.slice(2));
    assert.deepEqual(recover({ base: 7, darts }, 10), []);
  });

  it("drops the queue when the board doesn't fit any more (someone else scored or undid)", () => {
    assert.equal(recover({ base: 7, darts }, 6), null);
    assert.equal(recover({ base: 7, darts }, 11), null);
  });
});
