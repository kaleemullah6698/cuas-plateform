import type { AlertView } from './protocol';

/**
 * The alert stream's home outside React (ARCHITECTURE.md §2.5.3). Alerts are never coalesced — each
 * is published on arrival, newest first — because an alert delayed to a frame boundary is an alert an
 * operator sees late. The list is capped so it cannot grow without bound over a shift.
 */
export class AlertStore {
  private readonly listeners = new Set<() => void>();
  private currentSnapshot: readonly AlertView[] = [];

  constructor(private readonly cap = 100) {}

  add(alert: AlertView): void {
    this.currentSnapshot = [alert, ...this.currentSnapshot].slice(0, this.cap);
    this.notify();
  }

  clear(): void {
    this.currentSnapshot = [];
    this.notify();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = (): readonly AlertView[] => this.currentSnapshot;

  private notify(): void {
    for (const listener of this.listeners) {
      listener();
    }
  }
}
