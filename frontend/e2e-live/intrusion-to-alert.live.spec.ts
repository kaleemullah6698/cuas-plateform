import { expect, test, type Page } from '@playwright/test';

/**
 * The M11 acceptance journey, driven through the real platform.
 *
 * The M9 spec (`e2e/intrusion-to-alert.spec.ts`) mocks the login REST and the WebSocket in the page.
 * That proves the SPA and nothing behind it. This one installs no route handlers whatsoever. The
 * operator signs in against auth-service through the gateway, the console's WebSocket is the
 * gateway's own `/ws` fan-out, and every track and alert asserted below travelled:
 *
 *   sensor-simulator -> sensor-ingestion -> Kafka -> track-correlation -> ai-orchestrator (mock
 *   inference) -> Kafka -> threat-assessment -> Kafka -> alert-service -> Kafka -> api-gateway
 *   -> WebSocket -> this browser
 *
 * Preconditions the runner is responsible for (see scripts/verify-platform.sh, which does both):
 *   - the infra + platform compose profiles are up and every service answers its readiness probe
 *   - a bootstrap administrator exists, and CUAS_BOOTSTRAP_ADMIN_PASSWORD is exported
 *   - `docker compose --profile sim run --rm sensor-simulator` has been, or is being, replayed
 *
 * The spec never fabricates data and never falls back to a demo mode. If the pipeline is broken it
 * fails, which is the whole point of it existing.
 */

const USERNAME = process.env.CUAS_BOOTSTRAP_ADMIN_USERNAME ?? 'admin';
const PASSWORD = process.env.CUAS_BOOTSTRAP_ADMIN_PASSWORD ?? '';

test.beforeAll(() => {
  // Fail loudly rather than as a mystery 401 twenty seconds later. The platform seeds no default
  // credential on purpose (ARCHITECTURE.md §7.7), so an unset password is a runner error, not a
  // platform fault, and the two must not look alike in the report.
  expect(
    PASSWORD,
    'CUAS_BOOTSTRAP_ADMIN_PASSWORD must be exported for the live E2E (see .env)',
  ).not.toBe('');
});

/** Steps 2–3 of the journey: open the console and authenticate as the bootstrap administrator. */
async function signIn(page: Page): Promise<void> {
  // Capture the login response so a refusal is reported as itself. Without this every cause — wrong
  // password, unreachable auth-service, rate limit — surfaces identically as "still on /login",
  // which cost real time to tell apart. The gateway rate-limits logins per account (§7.8), so a
  // 429 here means the *previous* runs used the bucket up and this one is being asked to wait.
  let loginStatus = 0;
  let retryAfter: string | null = null;
  page.on('response', (response) => {
    if (response.url().includes('/api/v1/auth/login')) {
      loginStatus = response.status();
      retryAfter = response.headers()['retry-after'] ?? null;
    }
  });

  await page.goto('/login');
  await page.getByLabel('Username').fill(USERNAME);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect
    .poll(() => loginStatus, { timeout: 30_000, message: 'no response to POST /api/v1/auth/login' })
    .toBeGreaterThan(0);

  if (loginStatus === 429) {
    throw new Error(
      `Sign-in was rate limited (429, retry-after ${retryAfter ?? 'unset'}s). The gateway limits ` +
        'logins per account (§7.8) and an earlier run used the bucket. This is the control working, ' +
        'not a platform fault — wait for the window and re-run.',
    );
  }
  expect(loginStatus, `POST /api/v1/auth/login returned ${loginStatus}`).toBe(200);

  // The console is reached only once auth-service has issued a token and user-service has resolved
  // the permissions inside it — the gRPC hop that M11 had to fix before any of this could run.
  await expect(page).toHaveURL(/\/(dashboard|tracks)/, { timeout: 60_000 });
}

test('an operator signs in and watches a real intrusion become a live track and a real alert', async ({
  page,
}) => {
  const frames: string[] = [];
  // Record the envelope types the gateway actually pushes, so a failure report can say which hop
  // stopped rather than only that nothing appeared.
  page.on('websocket', (ws) => {
    frames.push(`OPEN ${ws.url()}`);
    ws.on('framereceived', (frame) => {
      const text = String(frame.payload);
      const type = /"type"\s*:\s*"([A-Z]+)"/.exec(text)?.[1] ?? 'UNKNOWN';
      if (type !== 'HEARTBEAT') frames.push(`${type} ${text.slice(0, 160)}`);
    });
  });

  await signIn(page);

  // Navigated by clicking, never by page.goto. A goto reloads the SPA, which tears down the
  // WebSocket and empties the track and alert stores — the live buckets are filled from the raised
  // feed, so everything received so far would be discarded and the test would be asserting on a
  // fresh, empty console. Client-side routing keeps the one socket and its accumulated state.
  await page.getByRole('link', { name: 'Tracks' }).click();
  await expect(page).toHaveURL(/\/tracks/);

  // Steps 5–7: a real track from the replayed intrusion reaches the console over SNAPSHOT/DELTA.
  // Asserted on the count in the header rather than a hard-coded identifier: the simulator's track
  // ids are assigned by track-correlation at runtime, and a spec that knew them in advance would be
  // asserting on fabricated data again.
  //
  // Not `getByText('Live', {exact: true})`: the status element renders the dot, the word and the
  // counter as one node, so its text is "Live16/16 tracks" and an exact match never fires. The
  // counter is the better assertion anyway — it is the evidence, not the decoration.
  const counter = page.getByText(/[1-9]\d*\/[1-9]\d* tracks/);
  await expect(counter).toBeVisible({ timeout: 120_000 });

  // Step 6: the socket is live. The console shows "Offline" whenever the connection is lost or
  // degraded (§2.5.2), so its absence alongside a filled track list is the connected state.
  await expect(page.getByText('Offline')).toHaveCount(0);

  // Step 8–9: threat-assessment scored the track and alert-service raised for it. The console
  // derives a track's severity from the live alert stream, so a severity chip on a row is
  // end-of-pipeline evidence — the score crossed §6.3.4's MEDIUM raising floor.
  await expect(page.getByRole('row').nth(1)).toBeVisible({ timeout: 120_000 });
  await expect(
    page.getByRole('row').filter({ hasText: /MED|HIGH|CRIT/ }).first(),
  ).toBeVisible({ timeout: 120_000 });

  // Steps 10–11: the alert arrived over the same socket and is on screen in the Alert Center.
  // ALT- is alert-service's own reference format, assigned when it raised the alert, so seeing one
  // here is end-of-pipeline evidence and not something the console could have invented.
  await page.getByRole('link', { name: 'Alerts' }).click();
  await expect(page).toHaveURL(/\/alerts/);
  await expect(page.getByText(/ALT-\d+/).first()).toBeVisible({ timeout: 120_000 });

  // Step 12: the captured path, printed so the run is evidence and not just a green tick.
  console.log('--- gateway WebSocket frames observed by the browser ---');
  for (const frame of frames.slice(0, 40)) console.log(frame);

  expect(frames.some((f) => f.startsWith('CONNECTED'))).toBe(true);
  expect(frames.some((f) => f.startsWith('SNAPSHOT') || f.startsWith('DELTA'))).toBe(true);
});
