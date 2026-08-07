/**
 * The WebSocket wire protocol (ARCHITECTURE.md §3.5), mirroring the gateway's JSON envelope exactly.
 * A change to the gateway DTOs is meant to break the build here rather than surface as a runtime
 * surprise — these types are the frontend's half of the contract.
 */

export type Channel = 'tracks' | 'alerts' | 'sensors' | 'system';

export interface TrackView {
  trackId: string;
  sectorId: string;
  status: string;
  latitude: number | null;
  longitude: number | null;
  altitudeM: number | null;
  speedMs: number;
  headingDeg: number;
  classLabel: string | null;
  confidence: number | null;
  observedAt: string | null;
  lastSeen: string | null;
  detectionCount: number;
}

export interface AlertView {
  alertId: string;
  reference: string;
  severity: string;
  trackId: string;
  title: string;
  summary: string;
  raisedAt: string | null;
}

export interface SensorView {
  sensorId: string;
  status: string;
  lastSeen: string | null;
}

export interface TracksSnapshot {
  tracks: TrackView[];
}
export interface TracksDelta {
  changed: TrackView[];
  removed: string[];
}
export interface SensorsSnapshot {
  sensors: SensorView[];
}
export interface SensorsDelta {
  changed: SensorView[];
}

export type ServerMessage =
  | { type: 'CONNECTED'; sessionId: string; serverTime: string }
  | { type: 'SNAPSHOT'; channel: Channel; seq: number; payload: unknown }
  | { type: 'DELTA'; channel: Channel; seq: number; payload: unknown }
  | { type: 'ALERT'; alert: AlertView }
  | { type: 'HEARTBEAT'; serverTime: string }
  | { type: 'ERROR'; code: string; message: string; retryable: boolean };

export type ClientCommand =
  | { type: 'SUBSCRIBE'; channels: Channel[] }
  | { type: 'RESNAPSHOT'; channel: Channel }
  | { type: 'UNSUBSCRIBE'; channel: Channel };
