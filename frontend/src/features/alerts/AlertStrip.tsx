import { useRealtime } from '@/app/RealtimeContext';
import { useAlerts } from '@/realtime/hooks';

/**
 * The alert strip (ARCHITECTURE.md §2.5.3). Bound to the {@code AlertStore}, which publishes every
 * alert on arrival — so an alert appears here the instant it is raised, never delayed to a frame. The
 * left border carries the threat colour; the newest few are shown. This is the M9 thin surface, not
 * the full Alert Center (acknowledge/escalate flows are out of scope).
 */
export function AlertStrip() {
  const realtime = useRealtime();
  const alerts = useAlerts(realtime.alerts);

  if (alerts.length === 0) {
    return null;
  }

  return (
    <div role="region" aria-label="Alerts" style={styles.strip}>
      {alerts.slice(0, 5).map((alert) => (
        <div
          key={alert.alertId}
          style={{ ...styles.alert, borderLeftColor: colorFor(alert.severity) }}
        >
          <strong style={styles.reference}>{alert.reference}</strong>
          <span>{alert.title}</span>
        </div>
      ))}
    </div>
  );
}

function colorFor(severity: string): string {
  if (severity.includes('CRITICAL')) {
    return 'var(--threat-critical)';
  }
  if (severity.includes('HIGH')) {
    return 'var(--threat-high)';
  }
  if (severity.includes('MEDIUM')) {
    return 'var(--threat-medium)';
  }
  return 'var(--threat-low)';
}

const styles: Record<string, React.CSSProperties> = {
  strip: { display: 'flex', flexDirection: 'column', gap: '0.25rem', padding: '0.5rem' },
  alert: {
    display: 'flex',
    gap: '0.5rem',
    padding: '0.4rem 0.6rem',
    background: 'var(--panel)',
    borderLeft: '3px solid var(--threat-low)',
    borderRadius: '0.25rem',
    fontSize: '0.85rem',
  },
  reference: { color: 'var(--text-dim)' },
};
