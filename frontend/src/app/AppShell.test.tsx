// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppShell } from '@/app/AppShell';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { useAuthStore } from '@/auth/authStore';
import { testRealtime } from '@/test/testRealtime';

function renderShell(initialEntry = '/dashboard') {
  render(
    <RealtimeProvider realtime={testRealtime()}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/login" element={<div>LOGIN</div>} />
          <Route element={<AppShell />}>
            <Route path="/dashboard" element={<div>DASHBOARD</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </RealtimeProvider>,
  );
}

describe('AppShell', () => {
  beforeEach(() => useAuthStore.getState().clear());

  it('redirects an unauthenticated visitor to login', () => {
    renderShell();
    expect(screen.getByText('LOGIN')).toBeInTheDocument();
  });

  it('renders the command-centre chrome and an accessible nav for an authenticated operator', () => {
    useAuthStore.getState().setSession('jwt', 'sess');
    renderShell();

    expect(screen.getByText('DASHBOARD')).toBeInTheDocument();
    expect(screen.getByText('C-UAS Command')).toBeInTheDocument();
    // Icon-only nav links carry accessible names.
    expect(screen.getByRole('link', { name: 'Live Map' })).toHaveAttribute('href', '/map');
    expect(screen.getByRole('link', { name: 'Alerts' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Account menu' })).toBeInTheDocument();
  });
});
