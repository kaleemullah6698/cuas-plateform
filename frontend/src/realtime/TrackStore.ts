import type { TrackView } from './protocol';

/** Schedules a flush; the default coalesces to the next animation frame (§2.5.1). */
export type Scheduler = (flush: () => void) => void;

const rafScheduler: Scheduler = (flush) => {
  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(flush);
  } else {
    setTimeout(flush, 16);
  }
};

/**
 * The track stream's home outside React (ARCHITECTURE.md §2.5.1, §3.4) — the single most important
 * frontend performance decision. Deltas mutate a plain {@code Map} and mark the store dirty; a flush
 * coalesced to an animation frame publishes one new immutable snapshot, so a 20 Hz source across
 * hundreds of tracks produces at most ~60 React renders per second. Track updates never pass through
 * React state per message.
 *
 * <p>The scheduler is injectable so a test can flush deterministically instead of waiting on a frame.
 * The store is ring-capped (§3.7) so an 8-hour shift cannot grow it without bound.
 */
export class TrackStore {
  private readonly tracks = new Map<string, TrackView>();
  private readonly listeners = new Set<() => void>();
  private currentSnapshot: readonly TrackView[] = [];
  private dirty = false;

  constructor(
    private readonly schedule: Scheduler = rafScheduler,
    private readonly cap = 500,
  ) {}

  /** Replaces the whole set — the response to a SNAPSHOT. */
  replaceAll(tracks: TrackView[]): void {
    this.tracks.clear();
    for (const track of tracks) {
      this.tracks.set(track.trackId, track);
    }
    this.markDirty();
  }

  /** Applies a DELTA: upsert the changed tracks, drop the removed ones. */
  applyDelta(changed: TrackView[], removed: string[]): void {
    for (const track of changed) {
      this.tracks.set(track.trackId, track);
    }
    for (const id of removed) {
      this.tracks.delete(id);
    }
    this.evict();
    this.markDirty();
  }

  /** Drops any live-data so the UI can desaturate on disconnect without deleting the model. */
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = (): readonly TrackView[] => this.currentSnapshot;

  private evict(): void {
    while (this.tracks.size > this.cap) {
      const oldest = this.tracks.keys().next().value;
      if (oldest === undefined) {
        return;
      }
      this.tracks.delete(oldest);
    }
  }

  private markDirty(): void {
    if (this.dirty) {
      return;
    }
    this.dirty = true;
    this.schedule(this.flush);
  }

  private flush = (): void => {
    if (!this.dirty) {
      return;
    }
    this.currentSnapshot = Array.from(this.tracks.values());
    this.dirty = false;
    for (const listener of this.listeners) {
      listener();
    }
  };
}
