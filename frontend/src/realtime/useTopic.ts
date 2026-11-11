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
