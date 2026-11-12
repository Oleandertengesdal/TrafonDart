/*
 * TrafonDart service worker (see docs/ARCHITECTURE.md, "Installable app").
 *
 * - The app shell (index.html) and the hashed /assets/* files are cached, so the installed app opens even on a bad
 *   connection or offline, and its own "waking the server" screen shows while a sleeping server starts.
 * - /api/* and /ws are NEVER cached or touched: scores, sessions and live data always go to the server. (Darts typed
 *   offline are queued by the app itself, not here.)
 * - A new deploy is picked up in the background and used from the next start.
 */
const VERSION = "td-v1";
const SHELL = `${VERSION}-shell`;
const ASSETS = `${VERSION}-assets`;
const SHELL_FILES = ["/", "/manifest.webmanifest", "/favicon.svg", "/icons/icon-192.png", "/icons/icon-512.png"];
const MAX_ASSETS = 80;

/** How a request is handled: "network" (left alone), "shell" (app page), "asset" (cache first) or "static". */
function route(url, method, mode, origin) {
  if (method !== "GET") return "network";
  const u = new URL(url);
  if (u.origin !== origin) return "network";
  if (u.pathname.startsWith("/api/") || u.pathname === "/api" || u.pathname.startsWith("/ws")) return "network";
  if (u.pathname === "/sw.js") return "network";
  if (u.pathname.startsWith("/assets/")) return "asset";
  if (mode === "navigate") return "shell";
  if (SHELL_FILES.includes(u.pathname) || u.pathname.startsWith("/icons/")) return "static";
  return "network";
}
self.__tdRoute = route;

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(SHELL)
      .then((c) => c.addAll(SHELL_FILES))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((n) => !n.startsWith(VERSION)).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

async function trim(cacheName, max) {
  const cache = await caches.open(cacheName);
  const keys = await cache.keys();
  for (let i = 0; i < keys.length - max; i++) await cache.delete(keys[i]);
}

/**
 * Network first with a short timeout: a fresh index.html always matches the deployed assets, and on a slow or dead
 * connection the cached shell answers within 2.5 s instead of a blank screen.
 */
async function shell(request) {
  const cache = await caches.open(SHELL);
  const fresh = fetch(request)
    .then((res) => {
      if (res.ok && (res.headers.get("content-type") || "").includes("text/html")) void cache.put("/", res.clone());
      return res;
    })
    .catch(() => null);
  const timeout = new Promise((resolve) => setTimeout(() => resolve(null), 2500));
  const first = await Promise.race([fresh, timeout]);
  if (first) return first;
  const cached = await cache.match("/");
  if (cached) return cached;
  return (await fresh) || new Response("Offline", { status: 503, headers: { "Content-Type": "text/plain" } });
}

async function asset(request) {
  const cache = await caches.open(ASSETS);
  const cached = await cache.match(request);
  if (cached) return cached;
  const res = await fetch(request);
  if (res.ok) {
    await cache.put(request, res.clone());
    void trim(ASSETS, MAX_ASSETS);
  }
  return res;
}

async function staticFile(request) {
  const cache = await caches.open(SHELL);
  const cached = await cache.match(request);
  const fresh = fetch(request)
    .then((res) => {
      if (res.ok) void cache.put(request, res.clone());
      return res;
    })
    .catch(() => null);
  return cached || (await fresh) || new Response("", { status: 504 });
}

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const kind = route(req.url, req.method, req.mode, self.location.origin);
  if (kind === "network") return; // the browser handles it as if there were no service worker
  if (kind === "shell") event.respondWith(shell(req));
  else if (kind === "asset") event.respondWith(asset(req));
  else event.respondWith(staticFile(req));
});
