import { expect, test } from '@playwright/test';

/**
 * The M9 acceptance flow (ROADMAP M9): an operator signs in, the live track list fills, and a real
 * alert appears on the strip — the intrusion→alert path, verified through the real built SPA.
 *
 * <p>The login REST and the WebSocket are mocked in the browser, so this exercises the whole frontend
 * — router, auth, the external stores, the WebSocket client's subscribe/snapshot/delta/alert handling
 * — without standing up the backend. The gateway's own fan-out is covered by its Java WebSocket
 * integration test; here the contract between them is the JSON envelope, produced by hand below.
 */
const TRACK = {
  trackId: 'alpha-01',
  sectorId: 'sector-NE',
  status: 'CONFIRMED',
  latitude: 33.6,
  longitude: 73.0,
  altitudeM: 120,
  speedMs: 14,
  headingDeg: 270,
  classLabel: 'DJI_Mavic_3',
  confidence: 0.87,
  observedAt: null,
  lastSeen: null,
  detectionCount: 5,
};

const ALERT = {
  alertId: 'a1',
  reference: 'ALT-9912',
  severity: 'THREAT_LEVEL_CRITICAL',
  trackId: 'alpha-01',
  title: 'CRITICAL — TRK closing on SITE-1',
  summary: 'closing',
  raisedAt: null,
};

test('an operator signs in and sees a live track and a real alert', async ({ page }) => {
  // Mock the gateway-proxied login.
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'jwt',
        tokenType: 'Bearer',
        expiresAt: '2030-01-01T00:00:00Z',
        sessionId: 's1',
      }),
    }),
  );

  // Mock the fan-out: greet, then on SUBSCRIBE reply with a tracks snapshot, a delta, and an alert.
  await page.routeWebSocket('**/ws**', (ws) => {
    ws.onMessage((message) => {
      const command = JSON.parse(String(message));
      if (command.type === 'SUBSCRIBE') {
        ws.send(JSON.stringify({ type: 'SNAPSHOT', channel: 'tracks', seq: 1, payload: { tracks: [] } }));
        ws.send(
          JSON.stringify({
            type: 'DELTA',
            channel: 'tracks',
            seq: 2,
            payload: { changed: [TRACK], removed: [] },
          }),
        );
        ws.send(JSON.stringify({ type: 'ALERT', alert: ALERT }));
      }
    });
    ws.send(JSON.stringify({ type: 'CONNECTED', sessionId: 's1', serverTime: new Date().toISOString() }));
  });

  await page.goto('/login');
  await page.getByLabel('Username').fill('kullah');
  await page.getByLabel('Password').fill('secret');
  await page.getByRole('button', { name: 'Sign in' }).click();

  // The track streamed in via SNAPSHOT+DELTA, and the alert via ALERT.
  await expect(page.getByText('alpha-01')).toBeVisible();
  await expect(page.getByText('DJI_Mavic_3')).toBeVisible();
  await expect(page.getByText('ALT-9912')).toBeVisible();
});
