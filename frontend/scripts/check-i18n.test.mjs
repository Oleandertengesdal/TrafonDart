import { test } from "node:test";
import assert from "node:assert/strict";
import { compare, flatten } from "./check-i18n.mjs";

test("flatten produces dotted keys", () => {
  assert.deepEqual(flatten({ a: { b: "x" }, c: "y" }), [["a.b", "x"], ["c", "y"]]);
});

test("compare finds keys missing on either side and empty values", () => {
  const r = compare({ a: "1", b: { c: "" } }, { a: "1", d: "2" });
  assert.deepEqual(r.missingInB, ["b.c"]);
  assert.deepEqual(r.missingInA, ["d"]);
  assert.deepEqual(r.empty, ["b.c"]);
});
