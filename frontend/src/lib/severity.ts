/**
 * The threat ramp, normalised (ARCHITECTURE.md §2.5, §6.3.4). Severity arrives from several places
 * (an alert's `THREAT_LEVEL_*`, a track's classification) — this collapses them to one vocabulary the
 * whole console shares, so a colour or a label never drifts between the map, the table and the strip.
 */
export type Severity = 'critical' | 'high' | 'medium' | 'low' | 'unknown';

export const SEVERITY_ORDER: Severity[] = ['critical', 'high', 'medium', 'low', 'unknown'];

export function severityOf(raw: string | null | undefined): Severity {
  const value = (raw ?? '').toUpperCase();
  if (value.includes('CRITICAL')) return 'critical';
  if (value.includes('HIGH')) return 'high';
  if (value.includes('MEDIUM')) return 'medium';
  if (value.includes('LOW')) return 'low';
  return 'unknown';
}

/** The worst severity in a set — a cluster or an incident always shows its most dangerous member. */
export function worst(severities: Severity[]): Severity {
  for (const level of SEVERITY_ORDER) {
    if (severities.includes(level)) return level;
  }
  return 'unknown';
}

export const SEVERITY_LABEL: Record<Severity, string> = {
  critical: 'CRITICAL',
  high: 'HIGH',
  medium: 'MEDIUM',
  low: 'LOW',
  unknown: 'UNKNOWN',
};

/** The CSS-variable colour for a severity, for canvas/map rendering where a class won't reach. */
export const SEVERITY_COLOR: Record<Severity, string> = {
  critical: 'var(--color-critical)',
  high: 'var(--color-high)',
  medium: 'var(--color-medium)',
  low: 'var(--color-low)',
  unknown: 'var(--color-unknown)',
};
