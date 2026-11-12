// Static security checks over the frontend source and its deployment config. They catch the classic mistakes
// before they reach a browser: HTML injection sinks, unencoded URL segments, weak CSP, external scripts.
// Run with `npm test`.
import { strict as assert } from "node:assert";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, it } from "node:test";

const root = new URL("..", import.meta.url).pathname;

function sourceFiles(dir = join(root, "src")) {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) return sourceFiles(p);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.ts$/.test(name) ? [p] : [];
  });
}
const files = sourceFiles().map((p) => ({ path: p.slice(root.length), src: readFileSync(p, "utf8") }));

function offenders(pattern) {
  return files.flatMap(({ path, src }) =>
    src.split("\n").flatMap((line, i) => (pattern.test(line) && !line.trim().startsWith("//") && !line.trim().startsWith("*") ? [`${path}:${i + 1}: ${line.trim()}`] : [])),
  );
}

describe("frontend source", () => {
  it("has no HTML injection sinks", () => {
    assert.deepEqual(offenders(/dangerouslySetInnerHTML|\.innerHTML\s*=|outerHTML|insertAdjacentHTML|document\.write/), []);
  });

  it("never evaluates strings as code", () => {
    assert.deepEqual(offenders(/\beval\(|new Function\(|setTimeout\(\s*["'`]|setInterval\(\s*["'`]/), []);
  });

  it("keeps secrets out of browser storage", () => {
    assert.deepEqual(offenders(/(localStorage|sessionStorage)\.setItem\([^)]*(token|session|password|secret|jwt)/i), []);
    assert.deepEqual(offenders(/document\.cookie\s*=\s*[^"'`]*td_session/), []);
  });

  it("opens external links without giving them window.opener", () => {
    for (const { path, src } of files) {
      for (const m of src.matchAll(/<a\b[^>]*target=["{]+_blank[^>]*>/g)) {
        assert.match(m[0], /rel=["{][^>]*noopener/, `${path}: ${m[0]}`);
      }
    }
  });

  it("builds every API URL with dynamic parts through apiPath, so ids from the address bar can't change the route", () => {
    // e.g. api.post(`/api/matches/${id}/throws`) with id = "../admin/users/1/password-reset?" would hit another endpoint
    assert.deepEqual(offenders(/\bapi\.(get|post|put|patch|del)\s*(<[^>]*>)?\(\s*`[^`]*\$\{/), []);
  });

  it("only talks to its own origin", () => {
    const hits = offenders(/fetch\(\s*["'`]https?:|new WebSocket\(\s*["'`]wss?:\/\/(?!\$)/);
    assert.deepEqual(hits, []);
  });
});

describe("deployment config", () => {
  const vercel = JSON.parse(readFileSync(join(root, "vercel.json"), "utf8"));
  const headers = Object.fromEntries(
    vercel.headers.find((h) => h.source === "/(.*)").headers.map((h) => [h.key.toLowerCase(), h.value]),
  );
  const csp = Object.fromEntries(headers["content-security-policy"].split(";").map((d) => d.trim().split(/\s+/)).map(([k, ...v]) => [k, v]));

  it("sends a strict Content-Security-Policy", () => {
    assert.deepEqual(csp["default-src"], ["'self'"]);
    assert.deepEqual(csp["script-src"], ["'self'"], "no inline or remote scripts");
    assert.deepEqual(csp["object-src"], ["'none'"]);
    assert.deepEqual(csp["frame-ancestors"], ["'none'"]);
    assert.deepEqual(csp["base-uri"], ["'self'"]);
    assert.ok(!headers["content-security-policy"].includes("unsafe-eval"));
    for (const src of csp["connect-src"]) assert.match(src, /^('self'|wss:\/\/[a-z0-9.-]+)$/, `connect-src ${src}`);
  });

  it("points the socket and the /api rewrite at the same backend over TLS", () => {
    const api = vercel.rewrites.find((r) => r.source.startsWith("/api"));
    const apiHost = new URL(api.destination).host;
    assert.equal(new URL(api.destination).protocol, "https:");
    const wsHosts = csp["connect-src"].filter((s) => s.startsWith("wss://")).map((s) => new URL(s).host);
    assert.deepEqual(wsHosts, [apiHost]);
  });

  it("sends the other security headers", () => {
    assert.equal(headers["x-content-type-options"], "nosniff");
    assert.equal(headers["x-frame-options"], "DENY");
    assert.match(headers["strict-transport-security"], /max-age=\d{8,}/);
    assert.ok(headers["referrer-policy"]);
    assert.match(headers["permissions-policy"], /camera=\(\)/);
  });

  it("serves the app shell for client routes but never for assets", () => {
    const spa = vercel.rewrites.find((r) => r.destination === "/index.html");
    assert.ok(spa);
    assert.ok(spa.source.includes("assets/"), "missing assets must 404, not return HTML");
  });

  it("serves the service worker, manifest and icons as files, never as the app shell", () => {
    const spa = vercel.rewrites.find((r) => r.destination === "/index.html");
    const re = new RegExp(`^${spa.source.replace(/^\//, "")}$`);
    for (const path of ["sw.js", "manifest.webmanifest", "icons/icon-192.png", "favicon.svg", "assets/index.js"]) {
      assert.ok(!re.test(path), `${path} must not be rewritten to index.html`);
    }
    for (const path of ["", "matches/abc", "tv", "players/kari/recap"]) assert.ok(re.test(path), `${path} is an app route`);
  });

  it("never lets the service worker be cached by the CDN, so updates reach every tablet", () => {
    const sw = vercel.headers.find((h) => h.source === "/sw.js");
    assert.ok(sw, "headers for /sw.js");
    const cache = sw.headers.find((h) => h.key.toLowerCase() === "cache-control").value;
    assert.match(cache, /no-cache|no-store|max-age=0/);
  });

  it("is installable: manifest with icons, linked from index.html", () => {
    const html = readFileSync(join(root, "index.html"), "utf8");
    assert.match(html, /<link rel="manifest" href="\/manifest\.webmanifest"/);
    assert.match(html, /<link rel="apple-touch-icon" href="\/icons\/apple-touch-icon\.png"/);
    const manifest = JSON.parse(readFileSync(join(root, "public", "manifest.webmanifest"), "utf8"));
    assert.equal(manifest.start_url, "/");
    assert.equal(manifest.display, "standalone");
    const sizes = manifest.icons.map((i) => i.sizes);
    assert.ok(sizes.includes("192x192") && sizes.includes("512x512"));
    assert.ok(manifest.icons.some((i) => i.purpose === "maskable"));
    for (const icon of manifest.icons) assert.ok(statSync(join(root, "public", icon.src)).size > 0, icon.src);
    assert.deepEqual(csp["manifest-src"] ?? csp["default-src"], ["'self'"]);
    assert.deepEqual(csp["worker-src"] ?? csp["script-src"], ["'self'"]);
  });

  it("loads no third-party scripts, fonts or styles", () => {
    const html = readFileSync(join(root, "index.html"), "utf8");
    assert.doesNotMatch(html, /<script(?![^>]*src="\/src\/)[^>]*>/, "only the app's own module script");
    assert.doesNotMatch(html, /https?:\/\/(?!www\.w3\.org)/, "no external URLs in index.html");
  });

  it("keeps secrets out of the public build variables", () => {
    const example = readFileSync(join(root, ".env.example"), "utf8");
    for (const line of example.split("\n").filter((l) => /^VITE_/.test(l))) {
      assert.doesNotMatch(line, /SECRET|PASSWORD|TOKEN|KEY/i, line);
    }
    const used = [...new Set(files.flatMap(({ src }) => [...src.matchAll(/import\.meta\.env\.(VITE_\w+)/g)].map((m) => m[1])))];
    for (const name of used) assert.doesNotMatch(name, /SECRET|PASSWORD|TOKEN|PRIVATE/i, name);
  });
});
