import { useEffect } from "react";

interface Sentinel {
  released: boolean;
  release: () => Promise<void>;
}
interface NavLike {
  wakeLock?: { request: (type: "screen") => Promise<Sentinel> };
}
interface DocLike {
  visibilityState: string;
  addEventListener: (type: "visibilitychange", fn: () => void) => void;
  removeEventListener: (type: "visibilitychange", fn: () => void) => void;
}

/**
 * Screen Wake Lock: keeps the tablet from locking between turns. Browsers drop the lock whenever the page is hidden,
 * so it is taken again each time the page becomes visible while it is wanted. Where the API is missing or refused
 * (old Safari, battery saver) nothing happens – scoring works the same, the screen just may dim.
 */
export function createWakeLock(nav: NavLike, doc: DocLike) {
  let wanted = false;
  let sentinel: Sentinel | null = null;

  const request = async () => {
    if (!nav.wakeLock || !wanted || doc.visibilityState !== "visible") return;
    if (sentinel && !sentinel.released) return;
    try {
      sentinel = await nav.wakeLock.request("screen");
    } catch {
      sentinel = null;
    }
  };
  const onVisibility = () => {
    if (doc.visibilityState === "visible") void request();
  };

  return {
    supported: !!nav.wakeLock,
    async enable() {
      if (wanted) return;
      wanted = true;
      doc.addEventListener("visibilitychange", onVisibility);
      await request();
    },
    async disable() {
      wanted = false;
      doc.removeEventListener("visibilitychange", onVisibility);
      const s = sentinel;
      sentinel = null;
      if (s && !s.released) await s.release().catch(() => undefined);
    },
  };
}

/** Keeps the screen on while {@code active} (a match or game in progress, or TV mode). */
export function useWakeLock(active: boolean) {
  useEffect(() => {
    if (!active || typeof navigator === "undefined") return;
    const lock = createWakeLock(navigator as unknown as NavLike, document);
    void lock.enable();
    return () => void lock.disable();
  }, [active]);
}
