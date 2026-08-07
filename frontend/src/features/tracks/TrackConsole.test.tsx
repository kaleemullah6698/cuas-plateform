// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import type { TrackView } from '@/realtime/protocol';
import { testRealtime } from '@/test/testRealtime';
import { TrackConsole } from './TrackConsole';

function track(id: string): TrackView {
  return {
    trackId: id,
    sectorId: 'sector-NE',
    status: 'CONFIRMED',
    latitude: 33.6,
    longitude: 73,
    altitudeM: 120,
    speedMs: 14,
    headingDeg: 90,
    classLabel: 'DJI_Mavic_3',
    confidence: 0.87,
    observedAt: null,
    lastSeen: null,
    detectionCount: 3,
  };
}

describe('TrackConsole', () => {
  it('renders a row for each track in the store', async () => {
    const realtime = testRealtime();
    render(
      <RealtimeProvider realtime={realtime}>
        <MemoryRouter>
          <TrackConsole />
        </MemoryRouter>
      </RealtimeProvider>,
    );

    expect(screen.getByText('No live tracks')).toBeInTheDocument();

    realtime.tracks.applyDelta([track('alpha-01')], []);

    expect(await screen.findByText('alpha-01')).toBeInTheDocument(); // trackId sliced to 8 chars
    expect(screen.getByText('DJI_Mavic_3')).toBeInTheDocument();
    expect(screen.getByText('87%')).toBeInTheDocument();
  });
});
