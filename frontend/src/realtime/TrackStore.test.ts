import { describe, expect, it, vi } from 'vitest';
import type { TrackView } from './protocol';
import { TrackStore } from './TrackStore';

function track(id: string, speedMs = 10): TrackView {
  return {
    trackId: id,
    sectorId: 'NE',
    status: 'CONFIRMED',
    latitude: 33.6,
    longitude: 73,
    altitudeM: 120,
    speedMs,
    headingDeg: 90,
    classLabel: null,
    confidence: null,
    observedAt: null,
    lastSeen: null,
    detectionCount: 1,
  };
}

describe('TrackStore', () => {
  it('coalesces several deltas into a single flush and one notification', () => {
    let scheduled: (() => void) | null = null;
    const store = new TrackStore((flush) => {
      scheduled = flush;
    });
    const listener = vi.fn();
    store.subscribe(listener);

    store.applyDelta([track('t1')], []);
    store.applyDelta([track('t2')], []);
    expect(listener).not.toHaveBeenCalled(); // nothing published until the frame

    scheduled!();

    expect(listener).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot().map((t) => t.trackId)).toEqual(['t1', 't2']);
  });

  it('keeps the newest value for a track id and drops removed ids', () => {
    const store = new TrackStore((flush) => flush()); // flush synchronously
    store.applyDelta([track('t1', 10)], []);
    store.applyDelta([track('t1', 20)], []);
    expect(store.getSnapshot()[0]!.speedMs).toBe(20);

    store.applyDelta([], ['t1']);
    expect(store.getSnapshot()).toHaveLength(0);
  });

  it('publishes a new snapshot identity on flush so React re-renders', () => {
    const store = new TrackStore((flush) => flush());
    const before = store.getSnapshot();
    store.replaceAll([track('t1')]);
    expect(store.getSnapshot()).not.toBe(before);
  });

  it('ring-caps the track count, evicting the oldest', () => {
    const store = new TrackStore((flush) => flush(), 2);
    store.applyDelta([track('t1'), track('t2'), track('t3')], []);
    expect(store.getSnapshot().map((t) => t.trackId)).toEqual(['t2', 't3']);
  });
});
