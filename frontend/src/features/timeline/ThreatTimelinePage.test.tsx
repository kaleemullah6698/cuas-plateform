// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import type { Realtime } from '@/realtime/Realtime';
import { testRealtime } from '@/test/testRealtime';
import { ThreatTimelinePage } from './ThreatTimelinePage';

const RECORDED = {
  alertId: 'a-old',
  reference: 'ALT-1000',
  status: 'OPEN',
  severity: 'THREAT_LEVEL_HIGH',
  recommendation: 'RECOMMEND_OPERATOR_REVIEW',
  threatScore: 0.61,
  trackId: 't-1',
  sectorId: 'NE',
  assessmentId: null,
  title: 'HIGH — TRK closing on SITE-1',
  summary: 'closing',
  raisedAt: '2026-08-13T10:00:00Z',
  acknowledgedAt: null,
  resolvedAt: null,
  escalationLevel: 0,
  escalatesAt: null,
  occurrenceCount: 1,
  incidentId: null,
  assignedTo: null,
};

function renderPage(realtime: Realtime) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RealtimeProvider realtime={realtime}>
        <MemoryRouter>
          <ThreatTimelinePage />
        </MemoryRouter>
      </RealtimeProvider>
    </QueryClientProvider>,
  );
}

describe('ThreatTimelinePage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [RECORDED] })));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('shows recorded threat events with score and the §6.3.4 recommendation', async () => {
    renderPage(testRealtime());

    expect(await screen.findByText('ALT-1000')).toBeInTheDocument();
    expect(screen.getByText('0.610')).toBeInTheDocument();
    // The closed recommendation vocabulary, rendered readably but not renamed.
    expect(screen.getByText('Operator review')).toBeInTheDocument();
  });

  it('merges the live raised feed into the timeline while live-follow is on', async () => {
    const realtime = testRealtime();
    renderPage(realtime);
    await screen.findByText('ALT-1000');

    realtime.alerts.add({
      alertId: 'a-new',
      reference: 'ALT-2000',
      severity: 'THREAT_LEVEL_CRITICAL',
      trackId: 't-2',
      title: 'CRITICAL — new intrusion',
      summary: 'closing fast',
      raisedAt: '2026-08-13T11:00:00Z',
    });

    expect(await screen.findByText('ALT-2000')).toBeInTheDocument();
    // Newest first, so the live one leads.
    const refs = screen.getAllByText(/ALT-\d+/).map((n) => n.textContent);
    expect(refs[0]).toBe('ALT-2000');
  });

  /**
   * Screen 5 requires a permanent "HISTORICAL — NOT LIVE" banner whenever the view is not following
   * the live feed. Freezing is the honest version of that: alerts keep arriving, the view does not
   * move, and the banner says so rather than implying the airspace has gone quiet.
   */
  it('states plainly when the view is frozen rather than live', async () => {
    renderPage(testRealtime());
    await screen.findByText('ALT-1000');

    expect(screen.queryByText(/HISTORICAL — NOT LIVE/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Live-follow on/ }));
    expect(screen.getByText(/HISTORICAL — NOT LIVE/)).toBeInTheDocument();
  });
});
