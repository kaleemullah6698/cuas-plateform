import { type Severity, severityOf, worst } from '@/lib/severity';
import type { AlertView, SensorView } from '@/realtime/protocol';

/** Count of alerts by severity — the live threat picture (§2.4.1). Pure, so it is unit-tested directly. */
export function severityCounts(alerts: readonly AlertView[]): Record<Severity, number> {
  const counts: Record<Severity, number> = {
    critical: 0,
    high: 0,
    medium: 0,
    low: 0,
    unknown: 0,
  };
  for (const alert of alerts) {
    counts[severityOf(alert.severity)] += 1;
  }
  return counts;
}

/** The worst active severity, or null when there is nothing to show. */
export function worstAlertSeverity(alerts: readonly AlertView[]): Severity | null {
  if (alerts.length === 0) {
    return null;
  }
  return worst(alerts.map((alert) => severityOf(alert.severity)));
}

export interface SensorCounts {
  online: number;
  degraded: number;
  offline: number;
  total: number;
}

export function sensorCounts(sensors: readonly SensorView[]): SensorCounts {
  const counts: SensorCounts = { online: 0, degraded: 0, offline: 0, total: sensors.length };
  for (const sensor of sensors) {
    const status = sensor.status.toUpperCase();
    if (status.includes('OFFLINE')) {
      counts.offline += 1;
    } else if (status.includes('DEGRADED')) {
      counts.degraded += 1;
    } else {
      counts.online += 1;
    }
  }
  return counts;
}
