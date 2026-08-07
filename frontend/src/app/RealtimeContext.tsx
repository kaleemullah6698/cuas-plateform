import { createContext, type ReactNode, useContext, useRef } from 'react';
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
  const ref = useRef<Realtime>(realtime ?? new Realtime());
  return <RealtimeContext.Provider value={ref.current}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): Realtime {
  const realtime = useContext(RealtimeContext);
  if (!realtime) {
    throw new Error('useRealtime must be used within a RealtimeProvider');
  }
  return realtime;
}
