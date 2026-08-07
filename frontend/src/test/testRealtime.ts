import { Realtime } from '@/realtime/Realtime';
import type { SocketLike } from '@/realtime/WebSocketClient';

/** A socket that does nothing — component tests drive the stores directly, not the wire. */
class NoopSocket implements SocketLike {
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  send(): void {}
  close(): void {}
}

/** A {@link Realtime} wired to a no-op socket, so a component test can seed the stores without a broker. */
export function testRealtime(): Realtime {
  return new Realtime({ url: 'ws://test/ws', socketFactory: () => new NoopSocket() });
}
