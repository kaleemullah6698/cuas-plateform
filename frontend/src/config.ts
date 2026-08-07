/**
 * Runtime configuration (ARCHITECTURE.md §3.1). The console talks to the platform only through the
 * API gateway (§4.1.1): REST under the same origin, and the WebSocket at `/ws`. Both are overridable
 * at build time for a deployment that serves the gateway on another host.
 */
const rawApiBase = import.meta.env.VITE_API_BASE_URL ?? '';

export const config = {
  /** Base URL for REST calls to the gateway. Empty means same-origin (the gateway serves the SPA). */
  apiBaseUrl: rawApiBase,
  /** The WebSocket URL, derived from the API base unless given explicitly. */
  wsUrl: import.meta.env.VITE_WS_URL ?? deriveWsUrl(rawApiBase),
} as const;

function deriveWsUrl(apiBase: string): string {
  if (apiBase) {
    return `${apiBase.replace(/^http/, 'ws').replace(/\/$/, '')}/ws`;
  }
  if (typeof window === 'undefined') {
    return 'ws://localhost:8080/ws';
  }
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${window.location.host}/ws`;
}
