import { useEffect, useState } from 'react';
import { useRealtime } from '@/app/RealtimeContext';
import { useConnection } from '@/realtime/hooks';

/**
 * The connection-lost banner (ARCHITECTURE.md §2.5.2). When the stream is not live the console must not
 * present stale data as current: a full-width amber banner names the age of the last data and the
 * track glyphs desaturate (the track console dims when not live). A degraded connection — two seq gaps in the
 * window — shows a quieter notice.
 */
export function ConnectionBanner() {
  const realtime = useRealtime();
  const connection = useConnection(realtime.connection);
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  if (connection.status === 'CONNECTED') {
    return connection.degraded ? (
      <div role="status" style={styles.degraded}>
        DEGRADED CONNECTION — intermittent data gaps
      </div>
    ) : null;
  }

  const asOf = connection.lastMessageAt
    ? `${new Date(connection.lastMessageAt).toISOString().slice(11, 19)}Z`
    : '—';
  const age = connection.lastMessageAt ? formatAge(nowMs - connection.lastMessageAt) : null;

  return (
    <div role="alert" style={styles.lost}>
      CONNECTION LOST — DATA AS OF {asOf}
      {age ? ` (${age} AGO)` : ''}
    </div>
  );
}

function formatAge(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

const styles: Record<string, React.CSSProperties> = {
  lost: {
    padding: '0.4rem 1rem',
    background: 'var(--warn)',
    color: '#1b1300',
    fontWeight: 600,
    fontSize: '0.85rem',
    textAlign: 'center',
  },
  degraded: {
    padding: '0.3rem 1rem',
    background: 'color-mix(in srgb, var(--warn) 30%, transparent)',
    color: 'var(--warn)',
    fontSize: '0.8rem',
    textAlign: 'center',
  },
};
