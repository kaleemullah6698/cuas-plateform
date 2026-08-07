import { Navigate, Outlet } from 'react-router-dom';
import { useRealtime } from '@/app/RealtimeContext';
import { AlertStrip } from '@/features/alerts/AlertStrip';
import { ConnectionBanner } from '@/components/ConnectionBanner';
import { useAuthStore } from '@/auth/authStore';
import { useConnection } from '@/realtime/hooks';

/**
 * The authenticated shell (ARCHITECTURE.md §3.3, thinned for M9). Guards its routes: an unauthenticated
 * visitor is sent to the login screen — and because the access token is held in memory only (§7.3), a
 * page refresh returns to login rather than presenting a dead session. The shell frames the connection
 * banner, the alert strip and the routed content.
 */
export function ProtectedShell() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div style={styles.shell}>
      <header style={styles.header}>
        <span style={styles.brand}>C-UAS Console</span>
        <ConnectionStatus />
      </header>
      <ConnectionBanner />
      <AlertStrip />
      <main style={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}

function ConnectionStatus() {
  const realtime = useRealtime();
  const connection = useConnection(realtime.connection);
  const color =
    connection.status === 'CONNECTED'
      ? connection.degraded
        ? 'var(--warn)'
        : 'var(--ok)'
      : 'var(--down)';
  return (
    <span style={styles.status}>
      <span style={{ ...styles.dot, background: color }} aria-hidden="true" />
      {connection.status}
    </span>
  );
}

const styles: Record<string, React.CSSProperties> = {
  shell: { display: 'flex', flexDirection: 'column', height: '100%' },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '0.5rem 1rem',
    borderBottom: '1px solid var(--border)',
    background: 'var(--panel)',
  },
  brand: { fontWeight: 600, fontSize: '0.9rem' },
  status: { display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: 'var(--text-dim)' },
  dot: { width: '0.6rem', height: '0.6rem', borderRadius: '50%' },
  main: { flex: 1, padding: '1rem', overflow: 'auto' },
};
