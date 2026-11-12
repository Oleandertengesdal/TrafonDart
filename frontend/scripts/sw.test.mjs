// The service worker must never cache or answer API calls, live sockets or anything state-changing – only the app
// shell and its static files. Runs public/sw.js in a sandbox and asks it how it would handle each request.
import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";
import vm from "node:vm";

const source = readFileSync(new URL("../public/sw.js", import.meta.url), "utf8");
const listeners = {};
const self = { addEventListener: (type, fn) => (listeners[type] = fn), location: { origin: "https://dart.example" } };
vm.runInNewContext(source, { self, caches: {}, fetch: () => Promise.reject(new Error("no network in tests")), URL, Response, console, setTimeout });
const route = self.__tdRoute;
const O = "https://dart.example";

describe("service worker", () => {
  it("leaves every API call and the live socket alone", () => {
    for (const path of ["/api/auth/me", "/api/matches/1/throws", "/api/users/me/export", "/api", "/ws", "/ws/info"]) {
      assert.equal(route(O + path, "GET", "cors", O), "network", path);
      assert.equal(route(O + path, "GET", "navigate", O), "network", path);
    }
  });

  it("never handles anything but GET", () => {
    for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
      assert.equal(route(O + "/", method, "navigate", O), "network");
      assert.equal(route(O + "/assets/index-abc.js", method, "no-cors", O), "network");
    }
  });

  it("never touches other sites", () => {
    assert.equal(route("https://trafondart-api.onrender.com/api/x", "GET", "cors", O), "network");
    assert.equal(route("https://evil.example/", "GET", "navigate", O), "network");
  });

  it("serves the app shell for page loads and caches hashed assets", () => {
    assert.equal(route(O + "/", "GET", "navigate", O), "shell");
    assert.equal(route(O + "/matches/abc", "GET", "navigate", O), "shell");
    assert.equal(route(O + "/reset-password?token=x", "GET", "navigate", O), "shell");
    assert.equal(route(O + "/assets/index-abc.js", "GET", "no-cors", O), "asset");
    assert.equal(route(O + "/icons/icon-192.png", "GET", "no-cors", O), "static");
    assert.equal(route(O + "/manifest.webmanifest", "GET", "manifest", O), "static");
  });

  it("is never served from its own cache", () => {
    assert.equal(route(O + "/sw.js", "GET", "same-origin", O), "network");
  });

  it("registers the lifecycle and fetch handlers", () => {
    assert.deepEqual(Object.keys(listeners).sort(), ["activate", "fetch", "install"]);
  });

  it("only caches responses from the shell and asset handlers (no cache.put in the fetch path for API)", () => {
    assert.ok(!/api\/.*cache\.put|cache\.put.*api\//.test(source));
  });
});
