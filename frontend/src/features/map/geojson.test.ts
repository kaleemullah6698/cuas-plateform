import { describe, expect, it } from 'vitest';
import type { AlertView, TrackView } from '@/realtime/protocol';
import { destination, severityByTrack, toHeadingFeatures, toTrackFeatures } from './geojson';

function track(partial: Partial<TrackView> & { trackId: string }): TrackView {
  return {
    sectorId: 'S1',
    status: 'ACTIVE',
    latitude: 33.6,
    longitude: 73.0,
    altitudeM: 120,
    speedMs: 12,
    headingDeg: 90,
    classLabel: 'QUADCOPTER',
    confidence: 0.8,
    observedAt: null,
    lastSeen: null,
    detectionCount: 3,
    ...partial,
  };
}

function alert(trackId: string, severity: string): AlertView {
  return { alertId: `${trackId}-${severity}`, reference: 'ALT', severity, trackId, title: 't', summary: 's', raisedAt: null };
}

describe('severityByTrack', () => {
  it('maps each track to its worst active severity', () => {
    const map = severityByTrack([
      alert('t1', 'THREAT_LEVEL_LOW'),
      alert('t1', 'THREAT_LEVEL_CRITICAL'),
      alert('t2', 'THREAT_LEVEL_MEDIUM'),
    ]);
    expect(map.get('t1')).toBe('critical');
    expect(map.get('t2')).toBe('medium');
    expect(map.get('t3')).toBeUndefined();
  });

  it('ignores alerts with no track', () => {
    expect(severityByTrack([alert('', 'THREAT_LEVEL_HIGH')]).size).toBe(0);
  });
});

describe('toTrackFeatures', () => {
  it('routes critical and high tracks to the never-clustered priority source', () => {
    const severity = severityByTrack([alert('t1', 'THREAT_LEVEL_CRITICAL'), alert('t2', 'THREAT_LEVEL_LOW')]);
    const { priority, clusterable } = toTrackFeatures(
      [track({ trackId: 't1' }), track({ trackId: 't2' }), track({ trackId: 't3' })],
      severity,
      null,
    );
    expect(priority.features.map((f) => f.properties.trackId)).toEqual(['t1']);
    expect(clusterable.features.map((f) => f.properties.trackId).sort()).toEqual(['t2', 't3']);
  });

  it('defaults an un-alerted track to unknown severity', () => {
    const { clusterable } = toTrackFeatures([track({ trackId: 't9' })], new Map(), null);
    expect(clusterable.features[0]?.properties.severity).toBe('unknown');
    expect(clusterable.features[0]?.properties.hasAlert).toBe(false);
  });

  it('marks the selected track', () => {
    const { clusterable } = toTrackFeatures([track({ trackId: 't1' })], new Map(), 't1');
    expect(clusterable.features[0]?.properties.selected).toBe(true);
  });

  it('drops a track with no position rather than placing it at null island', () => {
    const { priority, clusterable } = toTrackFeatures(
      [track({ trackId: 't1', latitude: null, longitude: null })],
      new Map(),
      null,
    );
    expect(priority.features).toHaveLength(0);
    expect(clusterable.features).toHaveLength(0);
  });
});

describe('toHeadingFeatures', () => {
  it('emits one LineString per positioned track', () => {
    const fc = toHeadingFeatures([track({ trackId: 't1' }), track({ trackId: 't2', latitude: null })]);
    expect(fc.features).toHaveLength(1);
    expect(fc.features[0]?.geometry.coordinates).toHaveLength(2);
  });
});

describe('destination', () => {
  it('projects due east to a larger longitude at the same latitude', () => {
    const [lon, lat] = destination(0, 0, 90, 1000);
    expect(lon).toBeGreaterThan(0);
    expect(lat).toBeCloseTo(0, 5);
  });

  it('projects due north to a larger latitude at the same longitude', () => {
    const [lon, lat] = destination(0, 0, 0, 1000);
    expect(lat).toBeGreaterThan(0);
    expect(lon).toBeCloseTo(0, 5);
  });
});
