// Browser errors are reported to our own backend (which forwards them to Sentry). Nothing personal or secret goes
// along, and a broken page can't send a flood.
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { buildReport, createReporter, scrubPath } from "./errorReport.ts";

describe("error reports", () => {
  it("keep only the path, never the query or fragment", () => {
    assert.equal(scrubPath("/reset-password?token=secret123#x"), "/reset-password");
    assert.equal(scrubPath("/matches/abc"), "/matches/abc");
    assert.equal(scrubPath(""), "/");
  });

  it("are built from errors, strings and anything else thrown", () => {
    const e = new TypeError("x is undefined");
    const r = buildReport("error", e, "/matches/1?x=1", "1.4.0");
    assert.equal(r.kind, "error");
    assert.equal(r.message, "TypeError: x is undefined");
    assert.ok(r.stack && r.stack.length > 0);
    assert.equal(r.path, "/matches/1");
    assert.equal(r.release, "1.4.0");
    assert.equal(buildReport("unhandledrejection", "plain text", "/", "1").message, "plain text");
    assert.equal(buildReport("unhandledrejection", { weird: true }, "/", "1").message, "Non-error value thrown");
    assert.equal(buildReport("unhandledrejection", undefined, "/", "1").message, "Non-error value thrown");
  });

  it("are trimmed to what the server accepts", () => {
    const e = new Error("m".repeat(2000));
    e.stack = "s".repeat(20_000);
    const r = buildReport("boundary", e, "/" + "p".repeat(1000), "1");
    assert.ok(r.message.length <= 500);
    assert.ok((r.stack ?? "").length <= 8000);
    assert.ok(r.path.length <= 300);
  });

  it("drop query strings and e-mail addresses from messages and stacks", () => {
    const e = new Error("Failed to load /api/x?token=abc123 for kari@example.no");
    e.stack = "Error: boom\n    at f (https://app.example/assets/i.js?v=99:1:2)";
    const r = buildReport("error", e, "/", "1");
    assert.ok(!r.message.includes("abc123"));
    assert.ok(!r.message.includes("kari@example.no"));
    assert.ok(!(r.stack ?? "").includes("v=99"));
  });
});

describe("the reporter", () => {
  it("sends each distinct error once and at most five per page load", () => {
    const sent: string[] = [];
    const report = createReporter((r) => sent.push(r.message), () => "/", "1");
    report("error", new Error("same"));
    report("error", new Error("same"));
    assert.deepEqual(sent, ["Error: same"]);
    for (let i = 0; i < 10; i++) report("error", new Error(`e${i}`));
    assert.equal(sent.length, 5);
  });

  it("ignores noise nobody can fix", () => {
    const sent: string[] = [];
    const report = createReporter((r) => sent.push(r.message), () => "/", "1");
    report("error", new Error("ResizeObserver loop completed with undelivered notifications."));
    report("unhandledrejection", Object.assign(new Error("AbortError"), { name: "AbortError" }));
    report("error", new Error("Script error."));
    assert.deepEqual(sent, []);
  });

  it("never throws, even if sending does", () => {
    const report = createReporter(() => {
      throw new Error("offline");
    }, () => "/", "1");
    report("error", new Error("x"));
  });
});
