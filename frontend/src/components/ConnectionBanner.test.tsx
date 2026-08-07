// @vitest-environment jsdom
import { act, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { testRealtime } from '@/test/testRealtime';
import { ConnectionBanner } from './ConnectionBanner';

describe('ConnectionBanner', () => {
  it('shows the connection-lost banner when the stream is not live, and hides it when live', () => {
    const realtime = testRealtime();
    render(
      <RealtimeProvider realtime={realtime}>
        <ConnectionBanner />
      </RealtimeProvider>,
    );

    // The store starts DISCONNECTED, so the banner is present.
    expect(screen.getByRole('alert')).toHaveTextContent('CONNECTION LOST');

    act(() => realtime.connection.setStatus('CONNECTED', false));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    act(() => realtime.connection.setStatus('CONNECTED', true));
    expect(screen.getByRole('status')).toHaveTextContent('DEGRADED');
  });
});
