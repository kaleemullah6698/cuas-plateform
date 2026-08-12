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

/**
 * M11 wired the gateway route for track-correlation, closing finding G-4 for this screen.
 *
 * The guard that used to assert this flag was `false` has been inverted rather than deleted. It is
 * still doing a job: the flag is the single switch between "the detail panel queries the gateway"
 * and "it shows an awaiting-integration state", and a silent flip back to `false` would take the
 * server-authoritative half of the track console off the air with no test failing.
 */
describe('M11 integration', () => {
  it('queries track-correlation through the gateway', () => {
    expect(TRACKS_REST_ROUTED).toBe(true);
  });
});
