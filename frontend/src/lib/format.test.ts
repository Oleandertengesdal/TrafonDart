// Number, date and label formatting in both languages. Run with `npm test`.
import { strict as assert } from "node:assert";
import { afterEach, describe, it } from "node:test";
import { setTestLanguage } from "../../scripts/test-support/i18n-stub.ts";
import { dartLabel, date, monthName, num, pct, relative, rulesSummary, signed } from "./format.ts";

// Intl may use narrow no-break spaces; compare with ordinary spaces.
const plain = (s: string) => s.replace(/[\u00a0\u202f]/g, " ");

afterEach(() => setTestLanguage("nb"));

describe("numbers", () => {
  it("uses a comma in Norwegian and a point in English", () => {
    setTestLanguage("nb");
    assert.equal(num(3.456, 2), "3,46");
    assert.equal(plain(num(12345)), "12 345");
    setTestLanguage("en");
    assert.equal(num(3.456, 2), "3.46");
    assert.equal(num(12345), "12,345");
  });

  it("formats percentages from 0–100 input", () => {
    setTestLanguage("en");
    assert.equal(pct(54.32), "54.3%");
    assert.equal(pct(100, 0), "100%");
    setTestLanguage("nb");
    assert.equal(plain(pct(54.32)), "54,3 %");
  });

  it("signs numbers with a real minus sign and leaves zero unsigned", () => {
    setTestLanguage("en");
    assert.equal(signed(12), "+12");
    assert.equal(signed(-4), "−4");
    assert.equal(signed(0), "0");
    assert.equal(signed(-0.04, 1), "0.0", "something that rounds to zero has no sign");
    assert.equal(signed(0.04, 1), "0.0");
    assert.equal(signed(6.86, 1), "+6.9");
  });
});

describe("dates", () => {
  it("names months in the active language", () => {
    setTestLanguage("nb");
    assert.equal(monthName("2026-11"), "november 2026");
    setTestLanguage("en");
    assert.equal(monthName("2026-01"), "January 2026");
    assert.equal(monthName(null), "–");
    assert.equal(monthName(""), "–");
  });

  it("never throws on missing values", () => {
    assert.equal(date(null), "–");
    assert.equal(relative(undefined), "–");
  });

  it("describes recent times relatively", () => {
    setTestLanguage("en");
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60_000).toISOString();
    assert.equal(relative(fiveMinutesAgo), "5 minutes ago");
    const old = new Date(Date.now() - 90 * 86_400_000).toISOString();
    assert.ok(!relative(old).includes("ago"), "old dates are shown as dates");
  });
});

describe("darts labels", () => {
  it("shows singles as plain numbers and keeps doubles and trebles", () => {
    assert.equal(dartLabel("S20"), "20");
    assert.equal(dartLabel("D16"), "D16");
    assert.equal(dartLabel("T19"), "T19");
    assert.equal(dartLabel("25"), "25");
    assert.equal(dartLabel("MISS"), "match.miss");
    assert.equal(dartLabel("BULL"), "match.bull");
  });

  it("summarises match rules without mentioning straight in", () => {
    const s = rulesSummary({ startScore: 501, inRule: "STRAIGHT", outRule: "DOUBLE", legsToWin: 3 });
    assert.ok(s.startsWith("501, match.out.DOUBLE"));
    assert.ok(!s.includes("match.in."));
    assert.ok(rulesSummary({ startScore: 301, inRule: "DOUBLE", outRule: "MASTER", legsToWin: 1 }).includes("match.in.DOUBLE"));
  });
});

describe("award periods", () => {
  it("names a whole year as just the year (Player of the Year) instead of crashing", async () => {
    const { monthName } = await import("./format.ts");
    assert.equal(monthName("2025"), "2025");
    assert.match(monthName("2025-10"), /2025/);
    assert.equal(monthName("garbage"), "–");
    assert.equal(monthName(""), "–");
  });
});
