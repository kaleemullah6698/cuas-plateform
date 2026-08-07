import { config } from '@/config';
import { AlertStore } from './AlertStore';
import { ConnectionStore } from './ConnectionStore';
import type { Channel } from './protocol';
import { TrackStore } from './TrackStore';
import { WebSocketClient, type WebSocketClientOptions } from './WebSocketClient';

const DEFAULT_CHANNELS: Channel[] = ['tracks', 'alerts', 'sensors'];

/**
 * Wires the one WebSocket connection to the three external stores (ARCHITECTURE.md §3.5). This is the
 * seam between transport and state: the client routes messages here, and here they land in the stores
 * the console reads. The thin console shows tracks and alerts; sensor updates are consumed (so the
 * subscription is honoured) but not surfaced in M9.
 */
export class Realtime {
  readonly tracks = new TrackStore();
  readonly alerts = new AlertStore();
  readonly connection = new ConnectionStore();
  private readonly client: WebSocketClient;

  constructor(overrides: Partial<WebSocketClientOptions> = {}) {
    this.client = new WebSocketClient({
      url: config.wsUrl,
      channels: DEFAULT_CHANNELS,
      handlers: {
        onTracksSnapshot: (tracks) => {
          this.tracks.replaceAll(tracks);
          this.connection.touch(Date.now());
        },
        onTracksDelta: (changed, removed) => {
          this.tracks.applyDelta(changed, removed);
          this.connection.touch(Date.now());
        },
        onSensorsSnapshot: () => {},
        onSensorsDelta: () => {},
        onAlert: (alert) => {
          this.alerts.add(alert);
          this.connection.touch(Date.now());
        },
        onStatus: (status, degraded) => this.connection.setStatus(status, degraded),
      },
      ...overrides,
    });
  }

  connect(token: string): void {
    this.client.connect(token);
  }

  updateToken(token: string): void {
    this.client.updateToken(token);
  }

  disconnect(): void {
    this.client.disconnect();
  }
}
