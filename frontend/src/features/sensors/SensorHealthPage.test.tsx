// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import type { Realtime } from '@/realtime/Realtime';
import { testRealtime } from '@/test/testRealtime';
import { SensorHealthPage } from './SensorHealthPage';

function renderPage(realtime: Realtime) {
  return render(
    <RealtimeProvider realtime={realtime}>
      <SensorHealthPage />
    </RealtimeProvider>,
  );
}

describe('SensorHealthPage', () => {
  afterEach(() => cleanup());

  it('shows an empty state before any sensor has reported', () => {
    renderPage(testRealtime());
    expect(screen.getByText(/No sensors have reported yet/)).toBeInTheDocument();
  });

  it('lists live sensors impaired-first and counts them by status', async () => {
    const realtime = testRealtime();
    renderPage(realtime);

    realtime.sensors.applyDelta([
      { sensorId: 'sim-rf-0', status: 'ONLINE', lastSeen: new Date().toISOString() },
      { sensorId: 'sim-eo-ir-2', status: 'OFFLINE', lastSeen: new Date().toISOString() },
      { sensorId: 'sim-radar-1', status: 'DEGRADED', lastSeen: new Date().toISOString() },
    ]);

    // findAll* lets React flush the external-store update before the assertion.
    const all = await screen.findAllByRole('row');
    // Coverage is the operational question: the failures sort to the top, not the healthy majority.
    // Asserted as one ordered string rather than by index, so the check reads as "this order" and
    // does not depend on indexed access.
    const order = all.slice(1).map((row) => row.textContent ?? '').join(' | ');
    expect(order).toMatch(/sim-eo-ir-2.*sim-radar-1.*sim-rf-0/s);

    expect(screen.getByText('1/3 online')).toBeInTheDocument();
  });

  it('renders the status the wire sent rather than inventing one', async () => {
    const realtime = testRealtime();
    renderPage(realtime);

    // The wire may carry the enum name; it must still read as the status, not as raw protobuf.
    realtime.sensors.applyDelta([
      { sensorId: 'sim-rf-0', status: 'SENSOR_STATUS_ONLINE', lastSeen: new Date().toISOString() },
    ]);

    expect(await screen.findByText('ONLINE')).toBeInTheDocument();
  });
});
