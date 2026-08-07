// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { testRealtime } from '@/test/testRealtime';
import { DashboardPage } from './DashboardPage';

describe('DashboardPage', () => {
  it('derives the live threat picture from the realtime stores', async () => {
    const realtime = testRealtime();
    render(
      <RealtimeProvider realtime={realtime}>
        <DashboardPage />
      </RealtimeProvider>,
    );

    // Empty state before any data.
    expect(screen.getByText('No active alerts — airspace clear.')).toBeInTheDocument();

    realtime.tracks.applyDelta(
      [
        {
          trackId: 't1',
          sectorId: 'NE',
          status: 'CONFIRMED',
          latitude: 33,
          longitude: 73,
          altitudeM: 100,
          speedMs: 12,
          headingDeg: 90,
          classLabel: null,
          confidence: null,
          observedAt: null,
          lastSeen: null,
          detectionCount: 1,
        },
      ],
      [],
    );
    realtime.alerts.add({
      alertId: 'a1',
      reference: 'ALT-1',
      severity: 'THREAT_LEVEL_CRITICAL',
      trackId: 't1',
      title: 'Critical approach',
      summary: 's',
      raisedAt: new Date().toISOString(),
    });

    expect(await screen.findByText('Critical approach')).toBeInTheDocument();
    expect(screen.getByText('Active Tracks')).toBeInTheDocument();
    // The critical alert drives the "Highest Threat" tile (CRIT appears in the tiles + the feed).
    expect(screen.getAllByText('CRIT').length).toBeGreaterThan(0);
  });
});
