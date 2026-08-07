import type { ConnectionStatus } from './WebSocketClient';

export interface ConnectionState {
  status: ConnectionStatus;
  /** True when two seq gaps occurred inside the window — the console shows a degraded indicator. */
  degraded: boolean;
  /** The epoch-ms of the last live message, for the "DATA AS OF … AGO" disconnect banner (§2.5.2). */
  lastMessageAt: number | null;
}

/**
 * The connection's status outside React (ARCHITECTURE.md §2.5.2). The console reads this to show the
 * connection-lost banner and desaturate track glyphs when the stream is not live — so stale data is
 * never presented as current.
 */
export class ConnectionStore {
  private readonly listeners = new Set<() => void>();
  private state: ConnectionState = { status: 'DISCONNECTED', degraded: false, lastMessageAt: null };

  setStatus(status: ConnectionStatus, degraded: boolean): void {
    this.state = { ...this.state, status, degraded };
    this.notify();
  }

  touch(at: number): void {
    this.state = { ...this.state, lastMessageAt: at };
    this.notify();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = (): ConnectionState => this.state;

  private notify(): void {
    for (const listener of this.listeners) {
      listener();
    }
  }
}
