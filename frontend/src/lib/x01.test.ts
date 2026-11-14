import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { START_SCORES } from "./x01.ts";

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : path.endsWith(".tsx") ? [path] : [];
  });
}

describe("X01 start scores", () => {
  it("are 301, 401, 501 and 701, as the server allows", () => {
    assert.deepEqual([...START_SCORES], [301, 401, 501, 701]);
  });

  it("every start-score picker offers them all (no hard-coded lists)", () => {
    const offenders = files("src").filter((f) => /\[\s*301\s*,/.test(readFileSync(f, "utf8")));
    assert.deepEqual(offenders, []);
  });
});
