import type { Feature, FeatureCollection, LineString } from 'geojson';
import type { Severity } from '@/lib/severity';
import type { TrackView } from '@/realtime/protocol';

type Coord = [number, number];

/**
 * Accumulates each track's recent positions into a trail, client-side (add-frontend-feature skill: the
 * stream carries only the current position, so the path is built here from what is observed — real
 * data, not fabricated history). Trails are capped so an 8-hour shift cannot grow them without bound,
 * and a track that is lost has its trail evicted rather than left dangling on the map.
 */
export class TrackTrails {
  private readonly trails = new Map<string, Coord[]>();

  constructor(private readonly cap = 40) {}

  /** Appends the current position of each present track, and forgets tracks that are gone. */
  record(tracks: readonly TrackView[]): void {
    const present = new Set<string>();
    for (const track of tracks) {
      if (track.latitude == null || track.longitude == null) {
        continue;
      }
      present.add(track.trackId);
      const trail = this.trails.get(track.trackId) ?? [];
      const last = trail[trail.length - 1];
      const point: Coord = [track.longitude, track.latitude];
      if (!last || last[0] !== point[0] || last[1] !== point[1]) {
        trail.push(point);
        if (trail.length > this.cap) {
          trail.shift();
        }
        this.trails.set(track.trackId, trail);
      }
    }
    for (const trackId of [...this.trails.keys()]) {
      if (!present.has(trackId)) {
        this.trails.delete(trackId);
      }
    }
  }

  /** One LineString per track with at least two points, coloured by the track's current severity. */
  toFeatures(severity: Map<string, Severity>): FeatureCollection<LineString> {
    const features: Feature<LineString>[] = [];
    for (const [trackId, coords] of this.trails) {
      if (coords.length < 2) {
        continue;
      }
      features.push({
        type: 'Feature',
        geometry: { type: 'LineString', coordinates: coords },
        properties: { trackId, severity: severity.get(trackId) ?? 'unknown' },
      });
    }
    return { type: 'FeatureCollection', features };
  }

  clear(): void {
    this.trails.clear();
  }
}
