import type {
  AlertView,
  Channel,
  ClientCommand,
  SensorsDelta,
  SensorsSnapshot,
  SensorView,
  ServerMessage,
  TracksDelta,
  TracksSnapshot,
  TrackView,
} from './protocol';

export type ConnectionStatus = 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED';

/** The minimal surface of a WebSocket the client uses, so a fake can stand in under test. */
export interface SocketLike {
  send(data: string): void;
  close(): void;
  onopen: (() => void) | null;
  onmessage: ((event: { data: string }) => void) | null;
  onclose: (() => void) | null;
  onerror: (() => void) | null;
}

export interface RealtimeHandlers {
  onTracksSnapshot(tracks: TrackView[]): void;
  onTracksDelta(changed: TrackView[], removed: string[]): void;
  onSensorsSnapshot(sensors: SensorView[]): void;
  onSensorsDelta(changed: SensorView[]): void;
  onAlert(alert: AlertView): void;
  onStatus(status: ConnectionStatus, degraded: boolean): void;
}

export interface WebSocketClientOptions {
  url: string;
  channels: Channel[];
  handlers: RealtimeHandlers;
  socketFactory?: (url: string) => SocketLike;
  now?: () => number;
  random?: () => number;
  heartbeatTimeoutMs?: number;
  backoffBaseMs?: number;
  backoffCapMs?: number;
  degradedWindowMs?: number;
}

/**
 * The single multiplexed WebSocket connection (ARCHITECTURE.md §2.5.2, §3.5).
 *
 * <p>Transport only: it authenticates the upgrade with the access token, subscribes to its channels,
 * routes {@code SNAPSHOT}/{@code DELTA}/{@code ALERT} to the handlers, and owns the three resilience
 * behaviours §2.5.2 specifies — a missed heartbeat (no message for the timeout) presumes the
 * connection dead and reconnects with exponential backoff and ±20% jitter; a {@code seq} gap requests
 * a resnapshot; two gaps inside the window raise the degraded indicator. On reconnect it re-subscribes
 * and takes a fresh snapshot — never a delta-resume — so a client can never silently diverge.
 */
export class WebSocketClient {
  private readonly socketFactory: (url: string) => SocketLike;
  private readonly now: () => number;
  private readonly random: () => number;
  private readonly heartbeatTimeoutMs: number;
  private readonly backoffBaseMs: number;
  private readonly backoffCapMs: number;
  private readonly degradedWindowMs: number;

  private socket: SocketLike | null = null;
  private token = '';
  private status: ConnectionStatus = 'DISCONNECTED';
  private degraded = false;
  private stopped = true;
  private attempt = 0;
  private readonly seq = new Map<Channel, number>();
  private gapTimestamps: number[] = [];
  private heartbeatTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  /** The last reconnect delay actually scheduled — exposed for tests and diagnostics. */
  lastReconnectDelayMs = 0;

  constructor(private readonly options: WebSocketClientOptions) {
    this.socketFactory = options.socketFactory ?? ((url) => new WebSocket(url) as unknown as SocketLike);
    this.now = options.now ?? Date.now;
    this.random = options.random ?? Math.random;
    this.heartbeatTimeoutMs = options.heartbeatTimeoutMs ?? 15_000;
    this.backoffBaseMs = options.backoffBaseMs ?? 1_000;
    this.backoffCapMs = options.backoffCapMs ?? 30_000;
    this.degradedWindowMs = options.degradedWindowMs ?? 30_000;
  }

  /** Opens the connection with an access token, and keeps it open (reconnecting) until disconnected. */
  connect(token: string): void {
    this.token = token;
    this.stopped = false;
    this.open();
  }

  /** Closes the connection for good — no reconnect. */
  disconnect(): void {
    this.stopped = true;
    this.clearTimers();
    this.socket?.close();
    this.socket = null;
    this.setStatus('DISCONNECTED');
  }

  private open(): void {
    this.clearReconnect();
    this.seq.clear();
    this.setStatus('CONNECTING');
    const socket = this.socketFactory(`${this.options.url}?token=${encodeURIComponent(this.token)}`);
    this.socket = socket;
    socket.onopen = () => this.handleOpen();
    socket.onmessage = (event) => this.handleMessage(event.data);
    socket.onclose = () => this.handleClose();
    socket.onerror = () => socket.close();
  }

  private handleOpen(): void {
    this.attempt = 0;
    this.setStatus('CONNECTED');
    this.send({ type: 'SUBSCRIBE', channels: this.options.channels });
    this.armHeartbeat();
  }

  private handleMessage(data: string): void {
    this.armHeartbeat();
    let message: ServerMessage;
    try {
      message = JSON.parse(data) as ServerMessage;
    } catch {
      return;
    }
    this.dispatch(message);
  }

  private dispatch(message: ServerMessage): void {
    switch (message.type) {
      case 'SNAPSHOT':
        this.seq.set(message.channel, message.seq);
        this.routeSnapshot(message.channel, message.payload);
        break;
      case 'DELTA':
        this.handleDelta(message.channel, message.seq, message.payload);
        break;
      case 'ALERT':
        this.options.handlers.onAlert(message.alert);
        break;
      case 'CONNECTED':
      case 'HEARTBEAT':
      case 'ERROR':
        break;
    }
  }

  private handleDelta(channel: Channel, seq: number, payload: unknown): void {
    const expected = (this.seq.get(channel) ?? 0) + 1;
    if (seq !== expected) {
      this.handleGap(channel);
      return;
    }
    this.seq.set(channel, seq);
    if (channel === 'tracks') {
      const delta = payload as TracksDelta;
      this.options.handlers.onTracksDelta(delta.changed, delta.removed);
    } else if (channel === 'sensors') {
      this.options.handlers.onSensorsDelta((payload as SensorsDelta).changed);
    }
  }

  private handleGap(channel: Channel): void {
    // A gap means we missed a delta; ask for a fresh snapshot rather than guess (§2.5.2).
    this.send({ type: 'RESNAPSHOT', channel });
    const now = this.now();
    this.gapTimestamps = this.gapTimestamps.filter((t) => now - t < this.degradedWindowMs);
    this.gapTimestamps.push(now);
    if (this.gapTimestamps.length >= 2) {
      // setStatus owns the degraded flag; setting it here first would make the change invisible.
      this.setStatus('CONNECTED', true);
    }
  }

  private routeSnapshot(channel: Channel, payload: unknown): void {
    if (channel === 'tracks') {
      this.options.handlers.onTracksSnapshot((payload as TracksSnapshot).tracks);
    } else if (channel === 'sensors') {
      this.options.handlers.onSensorsSnapshot((payload as SensorsSnapshot).sensors);
    }
  }

  private handleClose(): void {
    if (!this.stopped) {
      this.scheduleReconnect();
    }
  }

  private armHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearTimeout(this.heartbeatTimer);
    }
    this.heartbeatTimer = setTimeout(() => {
      // No message for the timeout: presume the connection dead and reconnect.
      this.socket?.close();
      this.scheduleReconnect();
    }, this.heartbeatTimeoutMs);
  }

  private scheduleReconnect(): void {
    if (this.stopped || this.reconnectTimer) {
      return;
    }
    this.clearHeartbeat();
    this.setStatus('DISCONNECTED');
    const delay = this.backoffDelay(this.attempt);
    this.lastReconnectDelayMs = delay;
    this.attempt += 1;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.open();
    }, delay);
  }

  /** Exponential backoff 1,2,4,8,… capped, with ±20% jitter (§2.5.2). */
  private backoffDelay(attempt: number): number {
    const base = Math.min(this.backoffBaseMs * 2 ** attempt, this.backoffCapMs);
    const jitter = 0.8 + this.random() * 0.4;
    return Math.round(base * jitter);
  }

  private send(command: ClientCommand): void {
    this.socket?.send(JSON.stringify(command));
  }

  private setStatus(status: ConnectionStatus, degraded: boolean = this.degraded): void {
    if (this.status === status && this.degraded === degraded) {
      return;
    }
    this.status = status;
    this.degraded = degraded;
    this.options.handlers.onStatus(status, degraded);
  }

  private clearHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearTimeout(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private clearReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private clearTimers(): void {
    this.clearHeartbeat();
    this.clearReconnect();
  }
}
