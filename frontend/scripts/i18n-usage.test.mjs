// Goes further than check-i18n.mjs: placeholders must match between languages, plural forms must be complete,
// and every translation key the source code asks for must exist. Run with `npm test`.
import { strict as assert } from "node:assert";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, it } from "node:test";

const root = new URL("..", import.meta.url).pathname;
const load = (lang) => JSON.parse(readFileSync(join(root, `src/i18n/locales/${lang}.json`), "utf8"));

function flatten(obj, prefix = "", out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === "object") flatten(v, key, out);
    else out[key] = String(v);
  }
  return out;
}

const nb = flatten(load("nb"));
const en = flatten(load("en"));
const PLURAL = /_(zero|one|two|few|many|other)$/;

function sourceFiles(dir = join(root, "src")) {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) return sourceFiles(p);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.ts$/.test(name) ? [p] : [];
  });
}

const placeholders = (s) => [...s.matchAll(/\{\{\s*([\w.]+)\s*(?:,[^}]*)?\}\}/g)].map((m) => m[1]).sort();

describe("translations", () => {
  it("use the same placeholders in Norwegian and English", () => {
    const mismatched = Object.keys(nb).filter((k) => k in en && placeholders(nb[k]).join() !== placeholders(en[k]).join());
    assert.deepEqual(mismatched, [], `placeholders differ: ${mismatched.map((k) => `${k} nb=${placeholders(nb[k])} en=${placeholders(en[k])}`).join("; ")}`);
  });

  it("have both _one and _other for every plural", () => {
    for (const dict of [nb, en]) {
      for (const key of Object.keys(dict)) {
        const m = key.match(PLURAL);
        if (!m) continue;
        const base = key.slice(0, -m[0].length);
        assert.ok(`${base}_one` in dict && `${base}_other` in dict, `${base} needs _one and _other`);
        assert.ok(dict[`${base}_other`].includes("{{count}}") || !dict[`${base}_one`].includes("{{count}}"), `${base}_other should show the count`);
      }
    }
  });

  it("never leave English text in the Norwegian file by accident", () => {
    const suspicious = Object.keys(nb).filter((k) => nb[k] === en[k] && /\b(the|and|you|your|with|match|player)\b/i.test(nb[k]));
    assert.deepEqual(suspicious, []);
  });

  it("exist for every key the code asks for", () => {
    const has = (key) => key in nb || `${key}_one` in nb || `${key}_other` in nb;
    const missing = [];
    const prefixesMissing = [];
    for (const file of sourceFiles()) {
      const src = readFileSync(file, "utf8");
      // t("a.b"), t('a.b'), i18n.t("a.b"), i18nKey="a.b"
      for (const m of src.matchAll(/\bt\(\s*["']([a-zA-Z][\w-]*(?:\.[\w-]+)+)["']/g)) if (!has(m[1])) missing.push(`${m[1]} (${file.slice(root.length)})`);
      for (const m of src.matchAll(/i18nKey=["']([\w.-]+)["']/g)) if (!has(m[1])) missing.push(`${m[1]} (${file.slice(root.length)})`);
      // t(`a.b.${x}`) – at least one key must start with the static prefix
      for (const m of src.matchAll(/\bt\(\s*`([a-zA-Z][\w.-]*\.)\$\{/g)) {
        if (!Object.keys(nb).some((k) => k.startsWith(m[1]))) prefixesMissing.push(`${m[1]}* (${file.slice(root.length)})`);
      }
    }
    assert.deepEqual(missing, [], "missing keys");
    assert.deepEqual(prefixesMissing, [], "dynamic keys with no translations at all");
  });

  it("cover every error code the API can return", () => {
    const java = readFileSync(join(root, "../backend/src/main/java/no/trafondart/common/ErrorCode.java"), "utf8");
    const start = java.indexOf("{", java.indexOf("enum ErrorCode")) + 1;
    const body = java.slice(start, java.indexOf(";", start));
    const codes = [...body.matchAll(/^\s*([A-Z][A-Z0-9_]+)\s*\(/gm)].map((m) => m[1]);
    assert.ok(codes.length > 20);
    for (const code of [...codes, "NETWORK_ERROR", "UNKNOWN", "BAD_PATH"]) {
      assert.ok(`errors.${code}` in nb, `nb: errors.${code}`);
      assert.ok(`errors.${code}` in en, `en: errors.${code}`);
    }
  });
});
