import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The track read API (ARCHITECTURE.md §4.1.5 — `GET /tracks/{id}`, `/{id}/history`). These are the
 * server-authoritative detail sources: the full track record and its observed-state history (each state
 * carrying the sensor that reported it — the genuine sensor-contribution data).
 *
 * <p><b>M11 integration point.</b> The gateway routes only `/api/v1/auth|users|audit` today; track-
 * correlation's `/tracks` is not yet gateway-routed (M9 finding G-4). Rather than fire requests that
 * cannot succeed, the queries are gated behind {@link TRACKS_REST_ROUTED}. The client, the types and
 * the hooks are complete; M11 wires the gateway route and flips the flag — a data-integration change,
 * not a rewrite. Until then the detail panel shows a clear "awaiting M11" state, never fabricated data.
 */
export const TRACKS_REST_ROUTED = true;

/** Mirrors track-correlation's {@code TrackResponse} DTO exactly (the wire contract). */
export interface TrackRecord {
  trackId: string;
  sectorId: string;
  status: string;
  latitude: number;
  longitude: number;
  altitudeM: number;
  speedMs: number;
  headingDeg: number;
  observedAt: string | null;
  firstSeen: string | null;
  lastSeen: string | null;
  detectionCount: number;
  lastDetectionId: string | null;
}

/** Mirrors track-correlation's {@code TrackStateResponse} DTO exactly. */
export interface TrackState {
  latitude: number;
  longitude: number;
  altitudeM: number;
  speedMs: number;
  headingDeg: number;
  observedAt: string | null;
  detectionId: string;
  sensorId: string;
}

export function useTrackRecord(trackId: string | null) {
  return useQuery({
    queryKey: ['track', trackId],
    enabled: TRACKS_REST_ROUTED && !!trackId,
    queryFn: () => api.get<TrackRecord>(`/api/v1/tracks/${encodeURIComponent(trackId as string)}`),
  });
}

export function useTrackHistory(trackId: string | null, limit = 200) {
  return useQuery({
    queryKey: ['track-history', trackId, limit],
    enabled: TRACKS_REST_ROUTED && !!trackId,
    queryFn: () =>
      api.get<TrackState[]>(
        `/api/v1/tracks/${encodeURIComponent(trackId as string)}/history?limit=${limit}`,
      ),
  });
}

export interface SensorContribution {
  sensorId: string;
  observations: number;
  lastObservedAt: string | null;
}

/** Distinct contributing sensors and how many observations each supplied — derived from the history. */
export function sensorContributions(history: readonly TrackState[]): SensorContribution[] {
  const bySensor = new Map<string, SensorContribution>();
  for (const state of history) {
    const existing = bySensor.get(state.sensorId);
    if (existing) {
      existing.observations += 1;
      if (isNewer(state.observedAt, existing.lastObservedAt)) {
        existing.lastObservedAt = state.observedAt;
      }
    } else {
      bySensor.set(state.sensorId, {
        sensorId: state.sensorId,
        observations: 1,
        lastObservedAt: state.observedAt,
      });
    }
  }
  return [...bySensor.values()].sort((a, b) => b.observations - a.observations);
}

function isNewer(candidate: string | null, current: string | null): boolean {
  if (!candidate) {
    return false;
  }
  if (!current) {
    return true;
  }
  return Date.parse(candidate) > Date.parse(current);
}
