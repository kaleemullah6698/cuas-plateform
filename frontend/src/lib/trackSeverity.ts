import { type Severity, severityOf, worst } from '@/lib/severity';
import type { AlertView } from '@/realtime/protocol';

/**
 * Worst active severity per track, derived from the live alert stream. A track has no threat of its
 * own — its severity is whatever the alerts raised against it say — so this derivation is shared by
 * every surface that colours a track (the map, the dashboard, the track console) rather than each
 * feature re-deriving it (features must not import each other; shared logic lives in {@code lib/}).
 */
export function severityByTrack(alerts: readonly AlertView[]): Map<string, Severity> {
  const grouped = new Map<string, Severity[]>();
  for (const alert of alerts) {
    if (!alert.trackId) {
      continue;
    }
    const list = grouped.get(alert.trackId) ?? [];
    list.push(severityOf(alert.severity));
    grouped.set(alert.trackId, list);
  }
  const result = new Map<string, Severity>();
  for (const [trackId, severities] of grouped) {
    result.set(trackId, worst(severities));
  }
  return result;
}
