import type { Feature, FeatureCollection, LineString, Point } from 'geojson';
import { type Severity, severityOf, worst } from '@/lib/severity';
import type { AlertView, TrackView } from '@/realtime/protocol';

/** Worst active severity per track, derived from the live alert stream (a track has no threat of its own). */
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

export interface TrackFeatureProps {
  trackId: string;
  severity: Severity;
  classLabel: string;
  heading: number;
  speed: number;
  selected: boolean;
  hasAlert: boolean;
}

/** Critical and High are never clustered (§2.5.4) — they go to their own always-individual source. */
const PRIORITY: Severity[] = ['critical', 'high'];

/**
 * Splits tracks into two point sources: {@code priority} (critical/high, never clustered so danger is
 * never hidden inside a cluster) and {@code clusterable} (everything else). A track with no position is
 * dropped rather than placed at null-island.
 */
export function toTrackFeatures(
  tracks: readonly TrackView[],
  severity: Map<string, Severity>,
  selectedTrackId: string | null,
): { priority: FeatureCollection<Point, TrackFeatureProps>; clusterable: FeatureCollection<Point, TrackFeatureProps> } {
  const priority: Feature<Point, TrackFeatureProps>[] = [];
  const clusterable: Feature<Point, TrackFeatureProps>[] = [];
  for (const track of tracks) {
    if (track.latitude == null || track.longitude == null) {
      continue;
    }
    const level = severity.get(track.trackId) ?? 'unknown';
    const feature: Feature<Point, TrackFeatureProps> = {
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [track.longitude, track.latitude] },
      properties: {
        trackId: track.trackId,
        severity: level,
        classLabel: track.classLabel ?? '',
        heading: track.headingDeg,
        speed: track.speedMs,
        selected: track.trackId === selectedTrackId,
        hasAlert: severity.has(track.trackId),
      },
    };
    (PRIORITY.includes(level) ? priority : clusterable).push(feature);
  }
  return { priority: collection(priority), clusterable: collection(clusterable) };
}

/** Short heading vectors, one per track, so an operator reads direction of travel at a glance. */
export function toHeadingFeatures(
  tracks: readonly TrackView[],
  lengthMeters = 350,
): FeatureCollection<LineString> {
  const features: Feature<LineString>[] = [];
  for (const track of tracks) {
    if (track.latitude == null || track.longitude == null) {
      continue;
    }
    const end = destination(track.latitude, track.longitude, track.headingDeg, lengthMeters);
    features.push({
      type: 'Feature',
      geometry: { type: 'LineString', coordinates: [[track.longitude, track.latitude], end] },
      properties: { trackId: track.trackId },
    });
  }
  return collection(features);
}

/** Great-circle destination point — accurate enough for a short heading tick at operational scale. */
export function destination(
  lat: number,
  lon: number,
  bearingDeg: number,
  distanceMeters: number,
): [number, number] {
  const R = 6_371_000;
  const d = distanceMeters / R;
  const brng = (bearingDeg * Math.PI) / 180;
  const lat1 = (lat * Math.PI) / 180;
  const lon1 = (lon * Math.PI) / 180;
  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(brng),
  );
  const lon2 =
    lon1 +
    Math.atan2(
      Math.sin(brng) * Math.sin(d) * Math.cos(lat1),
      Math.cos(d) - Math.sin(lat1) * Math.sin(lat2),
    );
  return [(lon2 * 180) / Math.PI, (lat2 * 180) / Math.PI];
}

function collection<G extends Point | LineString, P = Record<string, unknown>>(
  features: Feature<G, P>[],
): FeatureCollection<G, P> {
  return { type: 'FeatureCollection', features };
}
