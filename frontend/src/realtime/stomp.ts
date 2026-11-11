import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { api } from "../api/client";

/**
 * One shared STOMP connection for the whole app.
 *
 * The socket only receives. Before every (re)connect we fetch a 60-second ticket from /api/ws/ticket
 * (same-origin, cookie-authenticated) and pass it in the CONNECT frame – no cookie has to cross to the
 * backend's own domain, which keeps Safari's third-party-cookie blocking out of the picture.
 */

type Listener = (body: unknown) => void;

function socketUrl(): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined;
  if (configured) return configured;
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}/ws`;
}

class LiveConnection {
  private client: Client | null = null;
  private listeners = new Map<string, Set<Listener>>();
  private subscriptions = new Map<string, StompSubscription>();
  private statusListeners = new Set<(connected: boolean) => void>();
  connected = false;

  private ensureClient() {
    if (this.client) return;
    this.client = new Client({
      brokerURL: socketUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async (client) => {
        try {
          const { ticket } = await api.post<{ ticket: string }>("/api/ws/ticket");
          client.connectHeaders = { ticket };
        } catch {
          // Not logged in (or offline). Try again later.
          client.connectHeaders = {};
        }
      },
      onConnect: () => {
        this.setConnected(true);
        this.subscriptions.clear();
        for (const topic of this.listeners.keys()) this.subscribeTopic(topic);
      },
      onWebSocketClose: () => this.setConnected(false),
      onStompError: () => this.setConnected(false),
    });
    this.client.activate();
  }

  private setConnected(value: boolean) {
    this.connected = value;
    this.statusListeners.forEach((l) => l(value));
  }

  private subscribeTopic(topic: string) {
    if (!this.client?.connected || this.subscriptions.has(topic)) return;
    const sub = this.client.subscribe(topic, (msg: IMessage) => {
      let body: unknown = null;
      try {
        body = JSON.parse(msg.body);
      } catch {
        return;
      }
      this.listeners.get(topic)?.forEach((l) => l(body));
    });
    this.subscriptions.set(topic, sub);
  }

  subscribe(topic: string, listener: Listener): () => void {
    this.ensureClient();
    let set = this.listeners.get(topic);
    if (!set) {
      set = new Set();
      this.listeners.set(topic, set);
    }
    set.add(listener);
    this.subscribeTopic(topic);
    return () => {
      set!.delete(listener);
      if (set!.size === 0) {
        this.listeners.delete(topic);
        this.subscriptions.get(topic)?.unsubscribe();
        this.subscriptions.delete(topic);
      }
    };
  }

  onStatus(listener: (connected: boolean) => void): () => void {
    this.statusListeners.add(listener);
    return () => this.statusListeners.delete(listener);
  }

  /** Called on logout so the next user gets a fresh ticket. */
  async reset() {
    const c = this.client;
    this.client = null;
    this.subscriptions.clear();
    this.setConnected(false);
    await c?.deactivate();
    if (this.listeners.size > 0) this.ensureClient();
  }
}

export const live = new LiveConnection();
