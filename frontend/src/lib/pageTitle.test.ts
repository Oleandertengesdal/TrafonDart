// Tab titles: the page first, the app name last, so many open tabs stay tellable apart.
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { APP_NAME, matchTitle, pageTitle } from "./pageTitle.ts";

describe("tab titles", () => {
  it("put the page first and the app last", () => {
    assert.equal(pageTitle("Kamper"), `Kamper – ${APP_NAME}`);
    assert.equal(APP_NAME, "TrafonDart");
  });
  it("fall back to the app name", () => {
    assert.equal(pageTitle(), "TrafonDart");
    assert.equal(pageTitle(""), "TrafonDart");
    assert.equal(pageTitle("   "), "TrafonDart");
    assert.equal(pageTitle(null), "TrafonDart");
  });
  it("trim and shorten long titles", () => {
    assert.equal(pageTitle("  Spillere  "), "Spillere – TrafonDart");
    const long = pageTitle("x".repeat(200));
    assert.ok(long.length <= 72, String(long.length));
    assert.ok(long.endsWith("… – TrafonDart"));
  });
  it("show the live score of a match", () => {
    assert.equal(matchTitle(["Kari", "Ola"], [141, 60], true), "141–60 Kari mot Ola");
    assert.equal(matchTitle(["Kari", "Ola"], [0, 2], false, "mot"), "Kari mot Ola");
    assert.equal(matchTitle(["Kari", "Ola"], [5, 9], true, "vs"), "5–9 Kari vs Ola");
  });
});
