import { useSyncExternalStore } from 'react';
import type { AlertStore } from './AlertStore';
import type { ConnectionState, ConnectionStore } from './ConnectionStore';
import type { AlertView, TrackView } from './protocol';
import type { TrackStore } from './TrackStore';

/**
 * The React bindings for the external stores (ARCHITECTURE.md §3.4). {@code useSyncExternalStore}
 * subscribes a component to a store; the store publishes a new immutable snapshot only on a flush, so
 * a component re-renders once per flush, never once per message.
 */
export function useTracks(store: TrackStore): readonly TrackView[] {
  return useSyncExternalStore(store.subscribe, store.getSnapshot);
}

export function useAlerts(store: AlertStore): readonly AlertView[] {
  return useSyncExternalStore(store.subscribe, store.getSnapshot);
}

export function useConnection(store: ConnectionStore): ConnectionState {
  return useSyncExternalStore(store.subscribe, store.getSnapshot);
}
