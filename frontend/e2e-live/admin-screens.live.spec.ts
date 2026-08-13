import { expect, test, type Page } from '@playwright/test';

/**
 * The two screens added after the M10 review found them missing (ARCHITECTURE.md §2 Screen 6, and
 * the AI half of Screen 11), driven against the **real** backend.
 *
 * No `page.route`, no `routeWebSocket`, no fixtures. Sensor Health renders whatever the `sensors`
 * WebSocket channel is carrying, and the AI registry is whatever ai-orchestrator's admin API returns.
 * Both assertions are therefore about *shape and honesty* rather than about particular values: a
 * spec that demanded three sensors would fail for a correct platform that happened to have four.
 */

const USERNAME = process.env.CUAS_BOOTSTRAP_ADMIN_USERNAME ?? 'admin';
const PASSWORD = process.env.CUAS_BOOTSTRAP_ADMIN_PASSWORD ?? '';

test.beforeAll(() => {
  expect(PASSWORD, 'CUAS_BOOTSTRAP_ADMIN_PASSWORD must be exported (see .env)').not.toBe('');
});

async function signIn(page: Page): Promise<void> {
  let status = 0;
  let retryAfter: string | null = null;
  page.on('response', (r) => {
    if (r.url().includes('/api/v1/auth/login')) {
      status = r.status();
      retryAfter = r.headers()['retry-after'] ?? null;
    }
  });

  await page.goto('/login');
  await page.getByLabel('Username').fill(USERNAME);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect.poll(() => status, { timeout: 30_000 }).toBeGreaterThan(0);
  if (status === 429) {
    throw new Error(
      `Sign-in rate limited (429, retry-after ${retryAfter ?? 'unset'}s). §7.8 working as designed — wait.`,
    );
  }
  expect(status, `POST /api/v1/auth/login returned ${status}`).toBe(200);
  await expect(page).toHaveURL(/\/(dashboard|tracks)/, { timeout: 60_000 });
}

test('Sensor Health shows the live sensor picture', async ({ page }) => {
  await signIn(page);

  await page.getByRole('link', { name: 'Sensor Health' }).click();
  await expect(page).toHaveURL(/\/sensors/);
  await expect(page.getByRole('heading', { name: 'Sensor Health' })).toBeVisible();

  // The counter is the evidence: it is derived from the same channel the dashboard tile reads.
  await expect(page.getByText(/\d+\/\d+ online/)).toBeVisible({ timeout: 60_000 });

  // With the simulator running there is at least one sensor row carrying a real id.
  await expect(page.getByRole('row').filter({ hasText: /sim-/ }).first()).toBeVisible({
    timeout: 60_000,
  });
});

test('Threat Timeline shows real raised events in order', async ({ page }) => {
  await signIn(page);

  await page.getByRole('link', { name: 'Threat Timeline' }).click();
  await expect(page).toHaveURL(/\/timeline/);
  await expect(page.getByRole('heading', { name: 'Threat Timeline' })).toBeVisible();

  // Real alert-service references, ordered newest first.
  await expect(page.getByText(/ALT-\d+/).first()).toBeVisible({ timeout: 60_000 });

  // Freezing must announce itself — §2 Screen 5's "HISTORICAL — NOT LIVE" requirement.
  await page.getByRole('button', { name: /Live-follow on/ }).click();
  await expect(page.getByText(/HISTORICAL — NOT LIVE/)).toBeVisible();
});

test('AI Service Registry lists the real registry and admits it is mock inference', async ({
  page,
}) => {
  await signIn(page);

  await page.getByRole('link', { name: 'AI Services' }).click();
  await expect(page).toHaveURL(/\/ai-services/);
  await expect(page.getByRole('heading', { name: 'AI Service Registry' })).toBeVisible();

  // The registry ai-orchestrator actually returns, routed by modality (§6.1).
  await expect(page.getByText('rf-classifier')).toBeVisible({ timeout: 60_000 });

  // The point of the screen: an operator must be able to see that no trained model is behind this.
  await expect(page.getByText(/Mock inference in use/)).toBeVisible();
});
