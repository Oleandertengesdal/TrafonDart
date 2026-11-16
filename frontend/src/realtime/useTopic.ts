import { useEffect, useRef, useState } from "react";
import { live } from "./stomp";

/** Subscribe to a STOMP topic for the lifetime of the component. The latest handler is always used. */
export function useTopic<T>(topic: string | null, handler: (body: T) => void) {
  const ref = useRef(handler);
  useEffect(() => {
    ref.current = handler;
  });
  useEffect(() => {
    if (!topic) return;
    return live.subscribe(topic, (body) => ref.current(body as T));
  }, [topic]);
}

export function useLiveStatus(): boolean {
  const [connected, setConnected] = useState(live.connected);
  useEffect(() => live.onStatus(setConnected), []);
  return connected;
}

/**
 * Runs the handler when the live socket comes back after a drop. Pushes sent in the meantime are lost, so a page that
 * only listens would keep showing the old board (for good, if the dart it missed was the last one).
 */
export function useReconnect(handler: () => void) {
  const ref = useRef(handler);
  useEffect(() => {
    ref.current = handler;
  });
  useEffect(() => live.onReconnect(() => ref.current()), []);
}
