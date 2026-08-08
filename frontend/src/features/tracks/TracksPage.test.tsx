// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import type { Realtime } from '@/realtime/Realtime';
import type { TrackView } from '@/realtime/protocol';
import { useSelectionStore } from '@/store/selectionStore';
import { testRealtime } from '@/test/testRealtime';
import { TracksPage } from './TracksPage';
import { useTrackFilters } from './trackFilters';

function track(trackId: string, over: Partial<TrackView> = {}): TrackView {
  return {
    trackId,
    sectorId: 'NE',
    status: 'CONFIRMED',
    latitude: 33,
    longitude: 73,
    altitudeM: 120,
    speedMs: 14,
    headingDeg: 48,
    classLabel: 'Mavic 3',
    confidence: 0.87,
    observedAt: new Date().toISOString(),
    lastSeen: new Date().toISOString(),
    detectionCount: 5,
    ...over,
  };
}

function renderPage(realtime: Realtime) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RealtimeProvider realtime={realtime}>
        <TracksPage />
      </RealtimeProvider>
    </QueryClientProvider>,
  );
}

describe('TracksPage', () => {
  beforeEach(() => {
    // Global UI stores are singletons — reset the ones this page reads so tests don't leak into each other.
    useSelectionStore.getState().select(null);
    useTrackFilters.setState({ minSeverity: 'unknown', query: '' });
  });

  afterEach(() => cleanup());

  it('lists live tracks threat-first and shows the selected track detail', async () => {
    const realtime = testRealtime();
    renderPage(realtime);

    expect(screen.getByText('Select a track to see its detail.')).toBeInTheDocument();

    realtime.tracks.applyDelta([track('t-crit'), track('t-low', { classLabel: 'Bird', speedMs: 4 })], []);
    realtime.alerts.add({
      alertId: 'a1',
      reference: 'ALT-1',
      severity: 'THREAT_LEVEL_CRITICAL',
      trackId: 't-crit',
      title: 'Critical approach',
      summary: 's',
      raisedAt: new Date().toISOString(),
    });
    realtime.alerts.add({
      alertId: 'a2',
      reference: 'ALT-2',
      severity: 'THREAT_LEVEL_LOW',
      trackId: 't-low',
      title: 'Low',
      summary: 's',
      raisedAt: new Date().toISOString(),
    });

    const critCell = await screen.findByText('t-crit');
    const lowCell = screen.getByText('t-low');
    // Critical sorts above low.
    expect(critCell.compareDocumentPosition(lowCell) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    // Selecting the critical track opens its detail.
    fireEvent.click(critCell);
    const detail = screen.getByRole('complementary');
    expect(within(detail).getByText('Telemetry')).toBeInTheDocument();
    expect(within(detail).getByText('Sensor contributions & history')).toBeInTheDocument();
    // The history source is an M11 integration point, gated — its awaiting note is shown, not fake data.
    expect(within(detail).getByText(/Awaiting gateway route/)).toBeInTheDocument();
  });

  it('filters the queue by the threat floor', async () => {
    const realtime = testRealtime();
    renderPage(realtime);
    realtime.tracks.applyDelta([track('t-crit'), track('t-low')], []);
    realtime.alerts.add({
      alertId: 'a1',
      reference: 'ALT-1',
      severity: 'THREAT_LEVEL_CRITICAL',
      trackId: 't-crit',
      title: 'Critical',
      summary: 's',
      raisedAt: new Date().toISOString(),
    });

    expect(await screen.findByText('t-crit')).toBeInTheDocument();
    // t-low has no alert → unknown severity; raise the floor to HIGH and it drops out.
    fireEvent.change(screen.getByRole('combobox', { name: 'Minimum threat level' }), {
      target: { value: 'high' },
    });
    expect(screen.queryByText('t-low')).not.toBeInTheDocument();
    expect(screen.getByText('t-crit')).toBeInTheDocument();
  });
});
