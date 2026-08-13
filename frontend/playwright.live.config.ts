import { defineConfig, devices } from '@playwright/test';

/**
 * The M11 **live** E2E config — the same browser, none of the mocks.
 *
 * `playwright.config.ts` runs the M9 spec against a dev server with the login REST and the WebSocket
 * faked in the page, which proves the SPA. It cannot prove the platform: every byte it renders was
 * written by the test. This config points at the `web` container on :3000, whose nginx proxies `/api`
 * and `/ws` to the gateway, and installs no routes at all. Every track and every alert the spec
 * asserts on has come out of Kafka, through the gateway's fan-out, over a real WebSocket.
 *
 * It is deliberately a separate config rather than a flag: this one only passes with the whole stack
 * up, so it must never run in the default `npm run test:e2e`.
 *
 * Usage (from the repository root, with the platform running and the simulator replayed):
 *   npm --prefix frontend run test:e2e:live
 */
export default defineConfig({
  testDir: './e2e-live',
  reporter: 'list',
  // One worker: the spec asserts on shared platform state, not on per-test fixtures.
  workers: 1,
  timeout: 180_000,
  use: {
    baseURL: process.env.WEB_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ignoreHTTPSErrors: true,
  },
  // No webServer: the SPA under test is the built one the `web` container serves, not a dev build.
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
