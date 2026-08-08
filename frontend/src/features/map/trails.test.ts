import { describe, expect, it } from 'vitest';
import type { TrackView } from '@/realtime/protocol';
import { TrackTrails } from './trails';

function track(trackId: string, lon: number, lat: number): TrackView {
  return {
    trackId,
    sectorId: 'S1',
    status: 'ACTIVE',
    latitude: lat,
    longitude: lon,
    altitudeM: 100,
    speedMs: 10,
    headingDeg: 45,
    classLabel: null,
    confidence: null,
    observedAt: null,
    lastSeen: null,
    detectionCount: 1,
  };
}

describe('TrackTrails', () => {
  it('accumulates a moving track into a growing LineString', () => {
    const trails = new TrackTrails();
    trails.record([track('t1', 73.0, 33.6)]);
    trails.record([track('t1', 73.1, 33.6)]);
    const fc = trails.toFeatures(new Map());
    expect(fc.features).toHaveLength(1);
    expect(fc.features[0]?.geometry.coordinates).toEqual([
      [73.0, 33.6],
      [73.1, 33.6],
    ]);
  });

  it('does not duplicate a stationary position', () => {
    const trails = new TrackTrails();
    trails.record([track('t1', 73.0, 33.6)]);
    trails.record([track('t1', 73.0, 33.6)]);
    // one point only → no LineString yet (needs at least two)
    expect(trails.toFeatures(new Map()).features).toHaveLength(0);
  });

  it('caps a trail at its configured length', () => {
    const trails = new TrackTrails(3);
    for (let i = 0; i < 10; i++) {
      trails.record([track('t1', 73.0 + i * 0.01, 33.6)]);
    }
    expect(trails.toFeatures(new Map()).features[0]?.geometry.coordinates).toHaveLength(3);
  });

  it('evicts a track that is no longer present', () => {
    const trails = new TrackTrails();
    trails.record([track('t1', 73.0, 33.6)]);
    trails.record([track('t1', 73.1, 33.6)]);
    trails.record([]); // t1 gone
    expect(trails.toFeatures(new Map()).features).toHaveLength(0);
  });

  it('colours a trail by the track severity supplied', () => {
    const trails = new TrackTrails();
    trails.record([track('t1', 73.0, 33.6)]);
    trails.record([track('t1', 73.1, 33.6)]);
    const fc = trails.toFeatures(new Map([['t1', 'critical']]));
    expect(fc.features[0]?.properties?.severity).toBe('critical');
  });

  it('skips a track with no position', () => {
    const trails = new TrackTrails();
    const noPos = { ...track('t1', 0, 0), latitude: null, longitude: null };
    trails.record([noPos]);
    expect(trails.toFeatures(new Map()).features).toHaveLength(0);
  });
});
