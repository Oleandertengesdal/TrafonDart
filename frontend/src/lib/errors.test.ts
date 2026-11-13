// How every error is explained to the user, and when an expired session sends you back to the login page.
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { ApiError } from "../api/client.ts";
import { describeError, isNotFound, shouldExpireSession } from "./errors.ts";

const api = (code: string, status: number) => new ApiError({ code, status });

describe("describing an error", () => {
  it("tells offline apart from a sleeping server", () => {
    assert.equal(describeError(api("NETWORK_ERROR", 0), { online: false }).kind, "offline");
    assert.equal(describeError(api("NETWORK_ERROR", 0), { online: true }).kind, "waking");
    for (const s of [502, 503, 504]) assert.equal(describeError(api("UNKNOWN", s), { online: true }).kind, "waking", String(s));
  });

  it("offers a retry only where trying again can help", () => {
    for (const [err, retry] of [
      [api("NETWORK_ERROR", 0), true],
      [api("UNKNOWN", 503), true],
      [api("INTERNAL_ERROR", 500), true],
      [api("RATE_LIMITED", 429), true],
      [api("NOT_FOUND", 404), false],
      [api("FORBIDDEN", 403), false],
      [api("AUTH_REQUIRED", 401), false],
      [api("VALIDATION_FAILED", 400), false],
    ] as const) {
      assert.equal(describeError(err, { online: true }).retry, retry, `${err.code}`);
    }
  });

  it("sends an expired session to the login page", () => {
    const d = describeError(api("AUTH_REQUIRED", 401), { online: true });
    assert.equal(d.kind, "session");
    assert.equal(d.action, "login");
  });

  it("explains forbidden and missing things, with a way home", () => {
    assert.deepEqual(
      [describeError(api("FORBIDDEN", 403), { online: true }), describeError(api("NOT_FOUND", 404), { online: true })].map((d) => [d.kind, d.action]),
      [
        ["forbidden", "home"],
        ["notFound", "home"],
      ],
    );
  });

  it("treats a malformed id in the address as 'not found' on a detail page", () => {
    assert.equal(describeError(api("VALIDATION_FAILED", 400), { online: true, resource: true }).kind, "notFound");
    assert.equal(describeError(api("BAD_PATH", 0), { online: true, resource: true }).kind, "notFound");
    assert.equal(describeError(api("VALIDATION_FAILED", 400), { online: true }).kind, "invalid");
  });

  it("keeps the precise message for known business errors", () => {
    const d = describeError(api("MATCH_OUT_OF_SYNC", 409), { online: true });
    assert.equal(d.kind, "conflict");
    assert.equal(d.code, "MATCH_OUT_OF_SYNC");
    assert.equal(describeError(api("CHALLENGE_LIMIT", 409), { online: true }).code, "CHALLENGE_LIMIT");
  });

  it("calls a server crash a server crash, and anything thrown that isn't from the API 'unknown'", () => {
    assert.equal(describeError(api("INTERNAL_ERROR", 500), { online: true }).kind, "server");
    assert.equal(describeError(new TypeError("x"), { online: true }).kind, "unknown");
    assert.equal(describeError(undefined, { online: true }).kind, "unknown");
    assert.equal(describeError(api("RATE_LIMITED", 429), { online: true }).kind, "rateLimited");
  });

  it("recognises 'not found' for pages that show their own 404", () => {
    assert.ok(isNotFound(api("NOT_FOUND", 404)));
    assert.ok(isNotFound(api("VALIDATION_FAILED", 400)));
    assert.ok(isNotFound(api("BAD_PATH", 0)));
    assert.ok(!isNotFound(api("INTERNAL_ERROR", 500)));
    assert.ok(!isNotFound(new Error("x")));
  });
});

describe("an expired session", () => {
  it("logs you out when any request is refused as not logged in", () => {
    assert.equal(shouldExpireSession(api("AUTH_REQUIRED", 401), ["match", "abc"]), true);
    assert.equal(shouldExpireSession(api("AUTH_SESSION_REVOKED", 401), ["leaderboard"]), true);
  });
  it("but not for the 'who am I' check itself (that's how logging out is noticed) or other errors", () => {
    assert.equal(shouldExpireSession(api("AUTH_REQUIRED", 401), ["me"]), false);
    assert.equal(shouldExpireSession(api("FORBIDDEN", 403), ["match", "abc"]), false);
    assert.equal(shouldExpireSession(new Error("x"), ["match"]), false);
  });
});
