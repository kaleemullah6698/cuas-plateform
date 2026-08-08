import { describe, expect, it } from 'vitest';
import type { Severity } from '@/lib/severity';
import type { TrackView } from '@/realtime/protocol';
import { filterTracks, formatAge, severityRank, sortTracks } from './trackModel';

function track(trackId: string, over: Partial<TrackView> = {}): TrackView {
  return {
    trackId,
    sectorId: 'NE',
    status: 'CONFIRMED',
    latitude: 33,
    longitude: 73,
    altitudeM: 100,
    speedMs: 10,
    headingDeg: 90,
    classLabel: 'Quadcopter',
    confidence: 0.7,
    observedAt: null,
    lastSeen: null,
    detectionCount: 1,
    ...over,
  };
}

const sev = (entries: Array<[string, Severity]>) => new Map<string, Severity>(entries);

describe('severityRank', () => {
  it('orders critical highest and unknown lowest', () => {
    expect(severityRank('critical')).toBeGreaterThan(severityRank('high'));
    expect(severityRank('high')).toBeGreaterThan(severityRank('medium'));
    expect(severityRank('medium')).toBeGreaterThan(severityRank('low'));
    expect(severityRank('low')).toBeGreaterThan(severityRank('unknown'));
  });
});

describe('sortTracks', () => {
  it('sorts by threat descending, most-recently-seen breaking ties', () => {
    const tracks = [
      track('t-low', { lastSeen: '2026-08-08T10:00:00Z' }),
      track('t-crit'),
      track('t-high-old', { lastSeen: '2026-08-08T09:00:00Z' }),
      track('t-high-new', { lastSeen: '2026-08-08T09:30:00Z' }),
    ];
    const order = sortTracks(
      tracks,
      sev([
        ['t-crit', 'critical'],
        ['t-high-old', 'high'],
        ['t-high-new', 'high'],
        ['t-low', 'low'],
      ]),
    ).map((t) => t.trackId);
    expect(order).toEqual(['t-crit', 't-high-new', 't-high-old', 't-low']);
  });

  it('does not mutate its input', () => {
    const tracks = [track('a'), track('b')];
    const before = tracks.map((t) => t.trackId);
    sortTracks(tracks, sev([]));
    expect(tracks.map((t) => t.trackId)).toEqual(before);
  });
});

describe('filterTracks', () => {
  const tracks = [
    track('t-crit', { classLabel: 'Mavic 3' }),
    track('t-low', { classLabel: 'Bird' }),
    track('t-unk', { classLabel: null }),
  ];
  const severity = sev([
    ['t-crit', 'critical'],
    ['t-low', 'low'],
  ]);

  it('drops tracks below the minimum threat rank', () => {
    const result = filterTracks(tracks, severity, severityRank('high'), '');
    expect(result.map((t) => t.trackId)).toEqual(['t-crit']);
  });

  it('matches the query against id and class, case-insensitively', () => {
    expect(filterTracks(tracks, severity, severityRank('unknown'), 'mavic').map((t) => t.trackId)).toEqual([
      't-crit',
    ]);
    expect(filterTracks(tracks, severity, severityRank('unknown'), 'T-UNK').map((t) => t.trackId)).toEqual([
      't-unk',
    ]);
  });

  it('returns everything at the unknown floor with an empty query', () => {
    expect(filterTracks(tracks, severity, severityRank('unknown'), '')).toHaveLength(3);
  });
});

describe('formatAge', () => {
  const now = Date.parse('2026-08-08T12:00:00Z');
  it('renders seconds, minutes and hours compactly', () => {
    expect(formatAge('2026-08-08T11:59:30Z', now)).toBe('30s');
    expect(formatAge('2026-08-08T11:56:48Z', now)).toBe('3m12s');
    expect(formatAge('2026-08-08T10:30:00Z', now)).toBe('1h30m');
  });
  it('renders absent or invalid instants as ——', () => {
    expect(formatAge(null, now)).toBe('——');
    expect(formatAge('not-a-date', now)).toBe('——');
  });
});
