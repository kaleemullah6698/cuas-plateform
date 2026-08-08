import { type Severity, SEVERITY_ORDER } from '@/lib/severity';
import type { TrackView } from '@/realtime/protocol';

/**
 * The triage-queue logic for the track console (ARCHITECTURE.md §2.4.2). Pure functions over the live
 * track list and the alert-derived severity map, so the console's ordering and filtering are unit-
 * testable without a store or a render. Threat is the primary sort key — the most dangerous track is
 * always at the top of the queue (§2.4.2 "rows sort by threat descending by default").
 */

/** Numeric rank so severities compare and threshold-filter; higher is more dangerous. */
export function severityRank(severity: Severity): number {
  // SEVERITY_ORDER is worst-first; invert the index so critical is the largest rank.
  return SEVERITY_ORDER.length - 1 - SEVERITY_ORDER.indexOf(severity);
}

export function severityOfTrack(trackId: string, severity: Map<string, Severity>): Severity {
  return severity.get(trackId) ?? 'unknown';
}

/** Sort by threat descending, then most-recently-seen first, then by id for a stable order. */
export function sortTracks(
  tracks: readonly TrackView[],
  severity: Map<string, Severity>,
): TrackView[] {
  return [...tracks].sort((a, b) => {
    const byThreat =
      severityRank(severityOfTrack(b.trackId, severity)) -
      severityRank(severityOfTrack(a.trackId, severity));
    if (byThreat !== 0) {
      return byThreat;
    }
    const bySeen = seenMs(b) - seenMs(a);
    if (bySeen !== 0) {
      return bySeen;
    }
    return a.trackId.localeCompare(b.trackId);
  });
}

/** Filter by a minimum threat rank and a free-text match on id or classification. */
export function filterTracks(
  tracks: readonly TrackView[],
  severity: Map<string, Severity>,
  minRank: number,
  query: string,
): TrackView[] {
  const needle = query.trim().toLowerCase();
  return tracks.filter((track) => {
    if (severityRank(severityOfTrack(track.trackId, severity)) < minRank) {
      return false;
    }
    if (!needle) {
      return true;
    }
    return (
      track.trackId.toLowerCase().includes(needle) ||
      (track.classLabel ?? '').toLowerCase().includes(needle)
    );
  });
}

function seenMs(track: TrackView): number {
  const iso = track.lastSeen ?? track.observedAt;
  if (!iso) {
    return 0;
  }
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? 0 : ms;
}

/** Compact "3m12s" age from an ISO instant, measured against {@code now}. Absent → "——". */
export function formatAge(iso: string | null, now: number = Date.now()): string {
  if (!iso) {
    return '——';
  }
  const then = Date.parse(iso);
  if (Number.isNaN(then)) {
    return '——';
  }
  const totalSeconds = Math.max(0, Math.round((now - then) / 1000));
  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes < 60) {
    return `${minutes}m${String(seconds).padStart(2, '0')}s`;
  }
  const hours = Math.floor(minutes / 60);
  return `${hours}h${String(minutes % 60).padStart(2, '0')}m`;
}
