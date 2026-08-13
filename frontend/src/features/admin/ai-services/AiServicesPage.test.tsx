// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AiServicesPage } from './AiServicesPage';
import type { AiServiceRegistration } from './api/aiServicesApi';

const RF: AiServiceRegistration = {
  id: 'rf-classifier',
  modalities: ['RF'],
  mode: 'MOCK',
  endpoint: '',
  timeoutMs: 100,
  modelVersion: '',
  canaryVersion: '',
  canaryPercentage: 0,
  maxConcurrent: 32,
  healthy: true,
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AiServicesPage />
    </QueryClientProvider>,
  );
}

describe('AiServicesPage (live — ai-orchestrator admin API is gateway-routed)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [RF] })));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('lists the registry with its modality routing and per-service budget', async () => {
    renderPage();

    expect(await screen.findByText('rf-classifier')).toBeInTheDocument();
    // Routing is by modality — a fact about the sensor, never by service identity (§6.1).
    expect(screen.getByText('RF')).toBeInTheDocument();
    expect(screen.getByText('100 ms')).toBeInTheDocument();
    expect(screen.getByText('HEALTHY')).toBeInTheDocument();
  });

  /**
   * The most important assertion on this screen. §6.4's two-track switch must be visible: an operator
   * has to be able to tell that a classification came from the mock provider and not a trained model.
   */
  it('states plainly when the boundary is being served by mock inference', async () => {
    renderPage();

    expect(await screen.findByText(/Mock inference in use/)).toBeInTheDocument();
    expect(screen.getByText('MOCK')).toBeInTheDocument();
    // Twice, deliberately: once in the banner explaining what mock inference means, and once in the
    // row's model column, so the row is unambiguous when read on its own.
    expect(screen.getAllByText('0.0.0-mock')).toHaveLength(2);
  });

  it('explains an empty registry rather than showing a blank table', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [] })));
    renderPage();

    expect(await screen.findByText(/No AI services are registered/)).toBeInTheDocument();
  });
});
