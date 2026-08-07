// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { testRealtime } from '@/test/testRealtime';
import { AlertStrip } from './AlertStrip';

describe('AlertStrip', () => {
  it('shows an alert the instant it is added to the store', async () => {
    const realtime = testRealtime();
    render(
      <RealtimeProvider realtime={realtime}>
        <AlertStrip />
      </RealtimeProvider>,
    );

    // Nothing until an alert arrives.
    expect(screen.queryByRole('region', { name: 'Alerts' })).not.toBeInTheDocument();

    realtime.alerts.add({
      alertId: 'a1',
      reference: 'ALT-9912',
      severity: 'THREAT_LEVEL_CRITICAL',
      trackId: 't1',
      title: 'CRITICAL — TRK-0473 closing on SITE-1',
      summary: 'closing',
      raisedAt: null,
    });

    expect(await screen.findByText('ALT-9912')).toBeInTheDocument();
    expect(screen.getByText(/closing on SITE-1/)).toBeInTheDocument();
  });
});
