import { useEffect, useRef, useState } from 'react';
import type { TrackView } from '@/realtime/protocol';

export interface TelemetrySample {
  t: number;
  speedMs: number;
  altitudeM: number | null;
}

/**
 * A bounded, client-accumulated telemetry history for a single track — velocity and altitude over the
 * observed session. The stream carries only the current state (like the map's trails), so a trend is
 * built here from what is genuinely observed, never fabricated. It accumulates only while the operator
 * has the track open, is capped, and resets when the selection changes. This is a live-session view;
 * the authoritative server-side history (`/tracks/{id}/history`) is the M11 integration point below.
 */
export function useTelemetryHistory(track: TrackView | undefined, cap = 120): TelemetrySample[] {
  const trackId = track?.trackId;
  const bufferRef = useRef<TelemetrySample[]>([]);
  const lastKeyRef = useRef<string | null>(null);
  const [samples, setSamples] = useState<TelemetrySample[]>([]);

  // Reset the buffer when the selected track changes — one track's trend is not another's.
  useEffect(() => {
    bufferRef.current = [];
    lastKeyRef.current = null;
    setSamples([]);
  }, [trackId]);

  useEffect(() => {
    if (!track) {
      return;
    }
    // Only append on a genuinely new observation, keyed by the observation time when present.
    const key = track.observedAt ?? `${track.speedMs}:${track.altitudeM}:${track.headingDeg}`;
    if (key === lastKeyRef.current) {
      return;
    }
    lastKeyRef.current = key;
    const next = [
      ...bufferRef.current,
      { t: Date.now(), speedMs: track.speedMs, altitudeM: track.altitudeM },
    ];
    if (next.length > cap) {
      next.shift();
    }
    bufferRef.current = next;
    setSamples(next);
  }, [track, cap]);

  return samples;
}
