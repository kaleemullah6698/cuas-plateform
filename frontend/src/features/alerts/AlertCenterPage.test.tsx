// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { RealtimeProvider } from '@/app/RealtimeContext';
import type { Realtime } from '@/realtime/Realtime';
import { testRealtime } from '@/test/testRealtime';
import { AlertCenterPage } from './AlertCenterPage';

function renderPage(realtime: Realtime) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RealtimeProvider realtime={realtime}>
        <MemoryRouter>
          <AlertCenterPage />
        </MemoryRouter>
      </RealtimeProvider>
    </QueryClientProvider>,
  );
}

describe('AlertCenterPage', () => {
  afterEach(() => cleanup());

  it('fills the Unacknowledged bucket from the live raised feed and gates the workflow until M11', async () => {
    const realtime = testRealtime();
    renderPage(realtime);

    realtime.alerts.add({
      alertId: 'a1',
      reference: 'ALT-1',
      severity: 'THREAT_LEVEL_CRITICAL',
      trackId: 't1',
      title: 'Critical approach',
      summary: 's',
      raisedAt: new Date().toISOString(),
    });

    const alert = await screen.findByText('Critical approach');
    fireEvent.click(alert);

    // The lifecycle actions are present but disabled until the gateway routes alert-service (M11).
    const acknowledge = screen.getByRole('button', { name: 'Acknowledge' });
    expect(acknowledge).toBeDisabled();
    // The M11 explainer banner is shown.
    expect(screen.getByText(/live raised-alert feed/i)).toBeInTheDocument();
  });

  it('opens the source track (works today, no backend route needed)', async () => {
    const realtime = testRealtime();
    renderPage(realtime);
    realtime.alerts.add({
      alertId: 'a2',
      reference: 'ALT-2',
      severity: 'THREAT_LEVEL_HIGH',
      trackId: 't2',
      title: 'High track',
      summary: 's',
      raisedAt: new Date().toISOString(),
    });
    fireEvent.click(await screen.findByText('High track'));
    const openTrack = screen.getByRole('button', { name: /Open track/ });
    expect(openTrack).toBeEnabled();
    expect(within(openTrack).getByText('Open track')).toBeInTheDocument();
  });
});
