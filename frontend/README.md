# C-UAS Operator Console

React 19 + TypeScript + Vite + Tailwind. Specification: `docs/ARCHITECTURE.md`
sections 2 (UI/UX strategy) and 3 (frontend architecture).

## Status — M9 thin verification console

This is the **thin M9 build** (ROADMAP M9, Phase 3): a login screen, a live track
list, and an alert strip — enough to verify the backend and the WebSocket stream
end to end. The full §2 console (design system, map, charts, all screens) is a
later frontend phase and is deliberately **not** built here.

What exists:

| Area | What |
|---|---|
| `src/realtime/` | The streaming core — `WebSocketClient` (§2.5.2: subscribe, backoff+jitter reconnect, heartbeat-timeout, seq-gap→resnapshot, degraded), `TrackStore` (external store, rAF-coalesced, §3.4), `AlertStore`, `ConnectionStore`, and the `useSyncExternalStore` hooks. TypeScript envelope types mirror the gateway DTOs. |
| `src/auth/` | Login against the gateway-proxied auth-service (with the MFA branch); the access token is held in memory only (§7.3). |
| `src/app/`, `src/features/`, `src/components/` | `RealtimeContext`, `ProtectedShell`, `LoginPage`, `TrackConsole`, `AlertStrip`, `ConnectionBanner`. |
| `nginx.conf`, `Dockerfile` | Static hosting + reverse proxy of `/api` and `/ws` to the API gateway (same-origin browser). |

## Commands

```bash
npm install          # first run (Node 22); package-lock.json is committed
npm run dev          # Vite dev server on :3000
npm run build        # tsc + vite production build
npm run typecheck    # tsc --noEmit
npm test             # Vitest (unit + jsdom component tests)
npm run test:e2e     # Playwright — the intrusion→alert flow (needs a browser:
                     #   npx playwright install chromium)
```

The frontend is a **separate npm project**, not part of the Maven reactor —
`mvn clean verify` does not build or test it.

## Deferred (later frontend phase)

`src/design-system/` (§2.1 tokens/primitives/patterns), the MapLibre map, ECharts
charts, `src/api/generated/` protobuf types, `src/mocks/` MSW scenario replay, and
every §2.4 screen beyond the track list and alert strip.
