// Keeps the tablet's screen on during a match, and takes the lock back after the browser drops it.
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { createWakeLock } from "./wakeLock.ts";

function fakes(supported = true) {
  const log: string[] = [];
  let visibility = "visible";
  const listeners: Record<string, (() => void)[]> = {};
  const sentinels: { released: boolean; release: () => Promise<void>; onrelease?: () => void }[] = [];
  const nav = supported
    ? {
        wakeLock: {
          request: async (type: string) => {
            log.push(`request:${type}`);
            const s = {
              released: false,
              release: async () => {
                s.released = true;
                log.push("release");
              },
            };
            sentinels.push(s);
            return s;
          },
        },
      }
    : {};
  const doc = {
    get visibilityState() {
      return visibility;
    },
    addEventListener: (type: string, fn: () => void) => (listeners[type] ??= []).push(fn),
    removeEventListener: (type: string, fn: () => void) => (listeners[type] = (listeners[type] ?? []).filter((f) => f !== fn)),
  };
  return {
    nav,
    doc,
    log,
    sentinels,
    hide: () => {
      visibility = "hidden";
      // the browser drops screen locks when the page is hidden
      sentinels.forEach((s) => (s.released = true));
      listeners.visibilitychange?.forEach((f) => f());
    },
    show: () => {
      visibility = "visible";
      listeners.visibilitychange?.forEach((f) => f());
    },
    listenerCount: () => (listeners.visibilitychange ?? []).length,
  };
}

const tick = () => new Promise((r) => setTimeout(r, 0));

describe("screen wake lock", () => {
  it("asks for the screen lock when turned on and lets go when turned off", async () => {
    const f = fakes();
    const lock = createWakeLock(f.nav, f.doc);
    await lock.enable();
    assert.deepEqual(f.log, ["request:screen"]);
    await lock.disable();
    assert.deepEqual(f.log, ["request:screen", "release"]);
    assert.equal(f.listenerCount(), 0, "no listener left behind");
  });

  it("takes the lock again when the tablet comes back from the lock screen", async () => {
    const f = fakes();
    const lock = createWakeLock(f.nav, f.doc);
    await lock.enable();
    f.hide();
    f.show();
    await tick();
    assert.deepEqual(f.log, ["request:screen", "request:screen"]);
    await lock.disable();
  });

  it("does nothing when coming back if it was turned off", async () => {
    const f = fakes();
    const lock = createWakeLock(f.nav, f.doc);
    await lock.enable();
    await lock.disable();
    f.show();
    await tick();
    assert.deepEqual(f.log, ["request:screen", "release"]);
  });

  it("is harmless where the browser has no wake lock", async () => {
    const f = fakes(false);
    const lock = createWakeLock(f.nav, f.doc);
    assert.equal(lock.supported, false);
    await lock.enable();
    await lock.disable();
  });

  it("survives the browser refusing (e.g. battery saver)", async () => {
    const f = fakes();
    const nav = { wakeLock: { request: async () => Promise.reject(new Error("NotAllowedError")) } };
    const lock = createWakeLock(nav, f.doc);
    await lock.enable();
    await lock.disable();
  });
});
