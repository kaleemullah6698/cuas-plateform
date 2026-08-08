// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/auth/authStore';
import { AuditPage } from './AuditPage';

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AuditPage />
    </QueryClientProvider>,
  );
}

describe('AuditPage (live — audit-service is gateway-routed)', () => {
  beforeEach(() => {
    useAuthStore.setState({ perms: ['audit:read', 'audit:verify'], isAuthenticated: true });
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    useAuthStore.getState().clear();
  });

  it('renders a full-width critical banner when the chain is broken — the highest-severity condition', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'POST') {
          return {
            ok: true,
            status: 200,
            json: async () => ({
              intact: false,
              recordsVerified: 41,
              headHash: null,
              brokenAtSeq: 42,
              brokenAtAuditId: 'aud-42',
              reason: 'record hash does not chain to its predecessor',
            }),
          };
        }
        return { ok: true, status: 200, json: async () => [] };
      }),
    );

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Verify chain/ }));

    const banner = await screen.findByRole('alert');
    expect(banner).toHaveTextContent('AUDIT CHAIN BROKEN at seq 42');
  });
});
