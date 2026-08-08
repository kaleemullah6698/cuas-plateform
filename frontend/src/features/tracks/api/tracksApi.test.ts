import { describe, expect, it } from 'vitest';
import { sensorContributions, TRACKS_REST_ROUTED, type TrackState } from './tracksApi';

function state(sensorId: string, observedAt: string | null): TrackState {
  return {
    latitude: 33,
    longitude: 73,
    altitudeM: 100,
    speedMs: 10,
    headingDeg: 90,
    observedAt,
    detectionId: `${sensorId}-${observedAt}`,
    sensorId,
  };
}

describe('sensorContributions', () => {
  it('aggregates observations per sensor, most-active first, tracking the latest observation', () => {
    const result = sensorContributions([
      state('RF-01', '2026-08-08T10:00:00Z'),
      state('RAD-01', '2026-08-08T10:00:01Z'),
      state('RF-01', '2026-08-08T10:00:02Z'),
      state('RF-01', '2026-08-08T10:00:03Z'),
    ]);
    expect(result.map((c) => c.sensorId)).toEqual(['RF-01', 'RAD-01']);
    expect(result[0]).toMatchObject({ sensorId: 'RF-01', observations: 3, lastObservedAt: '2026-08-08T10:00:03Z' });
    expect(result[1]).toMatchObject({ sensorId: 'RAD-01', observations: 1 });
  });

  it('is empty for no history', () => {
    expect(sensorContributions([])).toEqual([]);
  });
});

describe('M11 integration boundary', () => {
  it('keeps the track REST queries gated until the gateway routes /tracks (M9 finding G-4)', () => {
    // A guard test: M11 flips this to true when the gateway route lands. Until then the detail panel
    // shows an "awaiting M11" state instead of firing requests that cannot succeed.
    expect(TRACKS_REST_ROUTED).toBe(false);
  });
});
