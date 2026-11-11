// Fails if nb.json and en.json don't have exactly the same keys, or if a value is empty.
// Run with `npm run i18n:check` (CI runs it on every push).
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "i18n", "locales");
const load = (lng) => JSON.parse(readFileSync(join(dir, `${lng}.json`), "utf8"));

export function flatten(obj, prefix = "") {
  return Object.entries(obj).flatMap(([k, v]) =>
    v && typeof v === "object" ? flatten(v, `${prefix}${k}.`) : [[`${prefix}${k}`, v]],
  );
}

export function compare(a, b) {
  const ka = new Map(flatten(a));
  const kb = new Map(flatten(b));
  const missingInB = [...ka.keys()].filter((k) => !kb.has(k));
  const missingInA = [...kb.keys()].filter((k) => !ka.has(k));
  const empty = [...ka, ...kb].filter(([, v]) => typeof v !== "string" || v.trim() === "").map(([k]) => k);
  return { missingInA, missingInB, empty };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { missingInA, missingInB, empty } = compare(load("nb"), load("en"));
  let ok = true;
  if (missingInB.length) { ok = false; console.error("Missing in en.json:\n  " + missingInB.join("\n  ")); }
  if (missingInA.length) { ok = false; console.error("Missing in nb.json:\n  " + missingInA.join("\n  ")); }
  if (empty.length) { ok = false; console.error("Empty translations:\n  " + empty.join("\n  ")); }
  if (!ok) process.exit(1);
  console.log(`i18n OK – ${flatten(load("nb")).length} keys in both nb and en.`);
}
