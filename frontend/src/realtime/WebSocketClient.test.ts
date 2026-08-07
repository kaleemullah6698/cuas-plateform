import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  type RealtimeHandlers,
  type SocketLike,
  WebSocketClient,
} from './WebSocketClient';

class FakeSocket implements SocketLike {
  readonly sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.onclose?.();
  }

  open(): void {
    this.onopen?.();
  }

  emit(message: object): void {
    this.onmessage?.({ data: JSON.stringify(message) });
  }

  lastSent(): unknown {
    return JSON.parse(this.sent[this.sent.length - 1]!);
  }
}

function stubHandlers(): RealtimeHandlers {
  return {
    onTracksSnapshot: vi.fn(),
    onTracksDelta: vi.fn(),
    onSensorsSnapshot: vi.fn(),
    onSensorsDelta: vi.fn(),
    onAlert: vi.fn(),
    onStatus: vi.fn(),
  };
}

describe('WebSocketClient', () => {
  let sockets: FakeSocket[];
  let handlers: RealtimeHandlers;

  function newClient() {
    return new WebSocketClient({
      url: 'ws://gateway/ws',
      channels: ['tracks', 'alerts'],
      handlers,
      socketFactory: () => {
        const socket = new FakeSocket();
        sockets.push(socket);
        return socket;
      },
      now: () => 0,
      random: () => 0.5, // jitter factor 1.0 → deterministic backoff
      heartbeatTimeoutMs: 15_000,
      backoffBaseMs: 1_000,
      backoffCapMs: 30_000,
    });
  }

  beforeEach(() => {
    sockets = [];
    handlers = stubHandlers();
  });

  it('subscribes on open and routes a snapshot then an in-order delta', () => {
    const client = newClient();
    client.connect('tok');
    const socket = sockets[0]!;
    socket.open();

    expect(handlers.onStatus).toHaveBeenCalledWith('CONNECTED', false);
    expect(socket.lastSent()).toEqual({ type: 'SUBSCRIBE', channels: ['tracks', 'alerts'] });

    socket.emit({ type: 'SNAPSHOT', channel: 'tracks', seq: 1, payload: { tracks: [] } });
    expect(handlers.onTracksSnapshot).toHaveBeenCalledWith([]);

    socket.emit({ type: 'DELTA', channel: 'tracks', seq: 2, payload: { changed: [], removed: ['x'] } });
    expect(handlers.onTracksDelta).toHaveBeenCalledWith([], ['x']);
  });

  it('requests a resnapshot on a seq gap and degrades after two gaps', () => {
    const client = newClient();
    client.connect('tok');
    const socket = sockets[0]!;
    socket.open();
    socket.emit({ type: 'SNAPSHOT', channel: 'tracks', seq: 1, payload: { tracks: [] } });

    // Expected next seq is 2; a 5 is a gap.
    socket.emit({ type: 'DELTA', channel: 'tracks', seq: 5, payload: { changed: [], removed: [] } });
    expect(socket.lastSent()).toEqual({ type: 'RESNAPSHOT', channel: 'tracks' });

    // A second gap inside the window raises the degraded indicator.
    socket.emit({ type: 'DELTA', channel: 'tracks', seq: 9, payload: { changed: [], removed: [] } });
    expect(handlers.onStatus).toHaveBeenCalledWith('CONNECTED', true);
  });

  it('pushes alerts through without a seq', () => {
    const client = newClient();
    client.connect('tok');
    sockets[0]!.open();
    const alert = {
      alertId: 'a1',
      reference: 'ALT-1',
      severity: 'CRITICAL',
      trackId: 't1',
      title: 't',
      summary: 's',
      raisedAt: null,
    };

    sockets[0]!.emit({ type: 'ALERT', alert });

    expect(handlers.onAlert).toHaveBeenCalledWith(alert);
  });

  describe('with fake timers', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('reconnects with backoff after a missed heartbeat', () => {
      const client = newClient();
      client.connect('tok');
      sockets[0]!.open();

      vi.advanceTimersByTime(15_000); // no message within the heartbeat timeout
      expect(handlers.onStatus).toHaveBeenCalledWith('DISCONNECTED', false);
      expect(client.lastReconnectDelayMs).toBe(1_000); // base 1000 × jitter 1.0

      vi.advanceTimersByTime(1_000);
      expect(sockets).toHaveLength(2); // a fresh connection was opened
    });
  });
});
