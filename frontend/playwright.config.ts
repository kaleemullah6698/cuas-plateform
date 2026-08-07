import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E config (ARCHITECTURE.md §3.7 testing). One spec: the intrusion→alert flow. The login
 * REST and the WebSocket are mocked in the browser (page.route / page.routeWebSocket), so the E2E
 * exercises the real built SPA end to end without standing up the backend — the gateway's own
 * behaviour is covered by its Java WebSocket integration test.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
