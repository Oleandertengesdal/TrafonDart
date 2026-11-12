// Unit tests for the fetch wrapper: CSRF handling, error mapping and path safety.
// Run with `npm test` (Node 22.18+ test runner; see scripts/test-support).
import { strict as assert } from "node:assert";
import { beforeEach, describe, it } from "node:test";

type Call = { url: string; init: RequestInit };
const calls: Call[] = [];
let jar: Record<string, string> = {};
let responder: (url: string, init: RequestInit) => Response | Promise<Response> = () => new Response(null, { status: 204 });

// A minimal browser: document.cookie behaves like a cookie jar, fetch is recorded.
(globalThis as Record<string, unknown>).document = {
  get cookie() {
    return Object.entries(jar).map(([k, v]) => `${k}=${v}`).join("; ");
  },
  set cookie(value: string) {
    const [pair, ...attrs] = value.split(";");
    const [name, v] = pair.split("=");
    if (attrs.some((a) => a.trim().toLowerCase() === "max-age=0")) delete jar[name.trim()];
    else jar[name.trim()] = v;
  },
};
globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = String(input);
  calls.push({ url, init: init ?? {} });
  if (url === "/api/auth/csrf") {
    jar["XSRF-TOKEN"] = `token-${calls.length}`;
    return new Response(null, { status: 204 });
  }
  return responder(url, init ?? {});
}) as typeof fetch;

const { api, ApiError, apiPath, shouldRetry, localiseDeleted } = await import("./client.ts");

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const headers = (c: Call) => (c.init.headers ?? {}) as Record<string, string>;

beforeEach(() => {
  calls.length = 0;
  jar = {};
  responder = () => json(200, { ok: true });
});

describe("api client", () => {
  it("fetches the CSRF cookie once before the very first call", async () => {
    await api.get("/api/auth/me");
    assert.equal(calls[0].url, "/api/auth/csrf");
    assert.equal(calls[1].url, "/api/auth/me");
    await api.get("/api/auth/me");
    assert.equal(calls.filter((c) => c.url === "/api/auth/csrf").length, 1);
  });

  it("sends the CSRF token on every state-changing method but never on GET", async () => {
    jar["XSRF-TOKEN"] = "abc";
    await api.get("/api/users");
    for (const send of [() => api.post("/api/x", { a: 1 }), () => api.put("/api/x"), () => api.patch("/api/x", {}), () => api.del("/api/x")]) {
      await send();
    }
    const [get, ...mutations] = calls;
    assert.equal(headers(get)["X-XSRF-TOKEN"], undefined);
    for (const m of mutations) assert.equal(headers(m)["X-XSRF-TOKEN"], "abc", `${m.init.method} carries the token`);
    assert.deepEqual(mutations.map((m) => m.init.method), ["POST", "PUT", "PATCH", "DELETE"]);
  });

  it("only ever sends same-origin credentials", async () => {
    jar["XSRF-TOKEN"] = "abc";
    await api.post("/api/x", {});
    for (const c of calls) assert.equal(c.init.credentials, "same-origin");
  });

  it("sends JSON bodies with the right content type", async () => {
    jar["XSRF-TOKEN"] = "abc";
    await api.post("/api/matches", { player1Id: 1 });
    assert.equal(headers(calls[0])["Content-Type"], "application/json");
    assert.equal(calls[0].init.body, JSON.stringify({ player1Id: 1 }));
  });

  it("retries exactly once with a fresh token after CSRF_INVALID", async () => {
    jar["XSRF-TOKEN"] = "stale";
    let attempts = 0;
    responder = () => {
      attempts++;
      return attempts === 1 ? json(403, { code: "CSRF_INVALID", status: 403 }) : json(200, { ok: true });
    };
    assert.deepEqual(await api.post("/api/x", {}), { ok: true });
    assert.equal(attempts, 2);
    assert.notEqual(headers(calls.at(-1)!)["X-XSRF-TOKEN"], "stale");
  });

  it("does not loop forever when the token keeps being refused", async () => {
    jar["XSRF-TOKEN"] = "stale";
    responder = () => json(403, { code: "CSRF_INVALID", status: 403 });
    await assert.rejects(api.post("/api/x", {}), (e: unknown) => e instanceof ApiError && e.code === "CSRF_INVALID");
    assert.ok(calls.filter((c) => c.url === "/api/x").length <= 2);
  });

  it("turns server errors into ApiError with a stable code", async () => {
    jar["XSRF-TOKEN"] = "abc";
    responder = () => json(400, { code: "VALIDATION_FAILED", status: 400, fieldErrors: [{ field: "username", code: "FORMAT" }] });
    await assert.rejects(api.post("/api/auth/register", {}), (e: unknown) => {
      assert.ok(e instanceof ApiError);
      assert.equal(e.code, "VALIDATION_FAILED");
      assert.equal(e.fieldError("username"), "FORMAT");
      assert.equal(e.fieldError("password"), undefined);
      return true;
    });
    responder = () => new Response("<html>Bad gateway</html>", { status: 502 });
    await assert.rejects(api.get("/api/x"), (e: unknown) => e instanceof ApiError && e.code === "INTERNAL_ERROR" && e.status === 502);
    responder = () => new Response("", { status: 404 });
    await assert.rejects(api.get("/api/x"), (e: unknown) => e instanceof ApiError && e.status === 404);
  });

  it("reports a network failure as NETWORK_ERROR", async () => {
    jar["XSRF-TOKEN"] = "abc";
    responder = () => {
      throw new TypeError("Failed to fetch");
    };
    await assert.rejects(api.get("/api/x"), (e: unknown) => e instanceof ApiError && e.code === "NETWORK_ERROR" && e.status === 0);
  });

  it("returns undefined for 204 No Content", async () => {
    jar["XSRF-TOKEN"] = "abc";
    responder = () => new Response(null, { status: 204 });
    assert.equal(await api.post("/api/auth/logout"), undefined);
  });

  it("refuses to call anything outside the same-origin API, or to walk out of it", async () => {
    for (const bad of [
      "https://evil.example/api/x",
      "//evil.example/api/x",
      "/api/matches/../admin/users",
      "/api/matches/%2e%2e/admin/users",
      "/api/matches/%2E%2E%2Fadmin",
      "/api/matches/..%2fadmin",
      "/api\\..\\admin",
      "/not-api/x",
    ]) {
      await assert.rejects(api.get(bad), (e: unknown) => e instanceof ApiError && e.code === "BAD_PATH", bad);
    }
    assert.equal(calls.length, 0, "nothing reached the network");
  });
});

describe("apiPath", () => {
  it("encodes every interpolated segment", () => {
    assert.equal(apiPath`/api/users/${"ola nordmann"}/stats`, "/api/users/ola%20nordmann/stats");
    assert.equal(apiPath`/api/users/${"a/b?c#d"}/stats`, "/api/users/a%2Fb%3Fc%23d/stats");
    assert.equal(apiPath`/api/comments/${42}/like`, "/api/comments/42/like");
  });

  it("refuses dot segments and empty values that would change the route", () => {
    for (const bad of ["..", ".", ""]) {
      assert.throws(() => apiPath`/api/matches/${bad}/throws`, (e: unknown) => e instanceof ApiError && e.code === "BAD_PATH", JSON.stringify(bad));
    }
    assert.throws(() => apiPath`/api/matches/${undefined as unknown as string}`, (e: unknown) => e instanceof ApiError && e.code === "BAD_PATH");
  });
});

describe("shouldRetry", () => {
  it("retries a waking server but never client errors or bad links", () => {
    assert.equal(shouldRetry(0, new ApiError({ code: "NETWORK_ERROR", status: 0 })), true);
    assert.equal(shouldRetry(2, new ApiError({ code: "INTERNAL_ERROR", status: 503 })), true);
    assert.equal(shouldRetry(6, new ApiError({ code: "INTERNAL_ERROR", status: 503 })), false);
    assert.equal(shouldRetry(0, new ApiError({ code: "NOT_FOUND", status: 404 })), false);
    assert.equal(shouldRetry(0, new ApiError({ code: "AUTH_REQUIRED", status: 401 })), false);
    assert.equal(shouldRetry(0, new ApiError({ code: "BAD_PATH", status: 0 })), false);
  });
});

describe("deleted accounts", () => {
  it("show a translated 'deleted player' wherever a player appears", async () => {
    responder = () =>
      json(200, {
        players: [
          { id: 1, username: "deleted~1", realName: "Slettet spiller", deleted: true },
          { id: 2, username: "kari", realName: "Kari", deleted: false },
        ],
        nested: { list: [{ author: { realName: "Slettet spiller", deleted: true } }] },
      });
    const body = await api.get<{ players: { realName: string }[]; nested: { list: { author: { realName: string } }[] } }>("/api/matches/1");
    assert.equal(body.players[0].realName, "player.deleted");
    assert.equal(body.players[1].realName, "Kari");
    assert.equal(body.nested.list[0].author.realName, "player.deleted");
  });

  it("leaves everything else alone", () => {
    const data = { realName: "Ola", deleted: "true", list: [1, "x", null], n: null };
    assert.deepEqual(localiseDeleted(structuredClone(data), "Deleted"), data);
    assert.equal(localiseDeleted("text", "Deleted"), "text");
    assert.equal(localiseDeleted(null, "Deleted"), null);
  });
});

describe("DELETE with a body", () => {
  it("sends the JSON body and the CSRF token", async () => {
    responder = () => new Response(null, { status: 204 });
    await api.del("/api/users/me", { password: "pw" });
    const call = calls.find((c) => c.url === "/api/users/me")!;
    assert.equal(call.init.method, "DELETE");
    assert.equal(call.init.body, JSON.stringify({ password: "pw" }));
    assert.ok(headers(call)["X-XSRF-TOKEN"]);
  });
});
