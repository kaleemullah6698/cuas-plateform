import { createContext, type ReactNode, useContext, useState } from 'react';
import { Realtime } from '@/realtime/Realtime';

const RealtimeContext = createContext<Realtime | null>(null);

/**
 * Provides the single {@link Realtime} instance to the tree (ARCHITECTURE.md §3.4). One connection and
 * one set of stores back the whole console. A test injects its own instance (with a fake socket) via
 * the {@code realtime} prop.
 */
export function RealtimeProvider({
  children,
  realtime,
}: {
  children: ReactNode;
  realtime?: Realtime;
}) {
  // Lazy state initialiser rather than a ref: exactly one Realtime per provider either way, but the
  // instance is created once during the first render instead of being read back out of a ref on
  // every render. `useState`'s initialiser runs once and the setter is never called, so this is a
  // constant for the provider's lifetime.
  const [instance] = useState<Realtime>(() => realtime ?? new Realtime());
  return <RealtimeContext.Provider value={instance}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): Realtime {
  const realtime = useContext(RealtimeContext);
  if (!realtime) {
    throw new Error('useRealtime must be used within a RealtimeProvider');
  }
  return realtime;
}
