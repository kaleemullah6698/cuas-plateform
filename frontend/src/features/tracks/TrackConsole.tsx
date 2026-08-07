import { useRealtime } from '@/app/RealtimeContext';
import { useConnection, useTracks } from '@/realtime/hooks';
import type { TrackView } from '@/realtime/protocol';

/**
 * The live track list (ARCHITECTURE.md §2.4, §2.5.1). Bound to the {@code TrackStore} external store,
 * so it re-renders once per coalesced flush, not once per message. When the stream is not live the
 * whole table desaturates (§2.5.2) — stale rows are shown as stale, never as current.
 *
 * <p>A plain table for the M9 thin verification; TanStack Virtual is the production swap at 200+ tracks
 * (§3.7), a rendering concern that does not change what is verified here.
 */
export function TrackConsole() {
  const realtime = useRealtime();
  const tracks = useTracks(realtime.tracks);
  const connection = useConnection(realtime.connection);
  const live = connection.status === 'CONNECTED';

  return (
    <section aria-label="Live tracks" style={{ opacity: live ? 1 : 0.5 }}>
      <table style={styles.table}>
        <thead>
          <tr>
            {['Track', 'Class', 'Confidence', 'Sector', 'Speed', 'Status'].map((heading) => (
              <th key={heading} style={styles.th}>
                {heading}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {tracks.length === 0 ? (
            <tr>
              <td colSpan={6} style={styles.empty}>
                No live tracks
              </td>
            </tr>
          ) : (
            tracks.map((track) => <TrackRow key={track.trackId} track={track} />)
          )}
        </tbody>
      </table>
    </section>
  );
}

function TrackRow({ track }: { track: TrackView }) {
  return (
    <tr>
      <td style={styles.td}>{track.trackId.slice(0, 8)}</td>
      <td style={styles.td}>{track.classLabel ?? '—'}</td>
      <td style={styles.td}>
        {track.confidence != null ? `${Math.round(track.confidence * 100)}%` : '——'}
      </td>
      <td style={styles.td}>{track.sectorId}</td>
      <td style={styles.td}>{track.speedMs.toFixed(0)} m/s</td>
      <td style={styles.td}>{track.status}</td>
    </tr>
  );
}

const styles: Record<string, React.CSSProperties> = {
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' },
  th: {
    textAlign: 'left',
    padding: '0.4rem 0.6rem',
    color: 'var(--text-dim)',
    borderBottom: '1px solid var(--border)',
    fontWeight: 600,
  },
  td: { padding: '0.4rem 0.6rem', borderBottom: '1px solid var(--border)', color: 'var(--text)' },
  empty: { padding: '1rem', color: 'var(--text-dim)', textAlign: 'center' },
};
