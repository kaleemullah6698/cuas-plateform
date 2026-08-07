// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { AuthError, login } from '@/auth/authClient';
import { useAuthStore } from '@/auth/authStore';
import { makeJwt } from '@/test/makeJwt';
import { testRealtime } from '@/test/testRealtime';
import { LoginPage } from './LoginPage';

vi.mock('@/auth/authClient', () => ({
  login: vi.fn(),
  verifyMfa: vi.fn(),
  AuthError: class AuthError extends Error {
    constructor(readonly status: number) {
      super(`auth ${status}`);
    }
  },
}));

function renderLogin() {
  render(
    <RealtimeProvider realtime={testRealtime()}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<div>DASHBOARD</div>} />
        </Routes>
      </MemoryRouter>
    </RealtimeProvider>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => useAuthStore.getState().clear());

  it('signs in, decodes the session, and enters the console', async () => {
    vi.mocked(login).mockResolvedValue({
      kind: 'authenticated',
      accessToken: makeJwt({ sub: 'usr-1', name: 'K. Ullah', perms: ['alert:read'] }),
      expiresAt: 'x',
      sessionId: 'sess',
    });
    renderLogin();

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'kullah' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('DASHBOARD')).toBeInTheDocument();
    expect(useAuthStore.getState().displayName).toBe('K. Ullah');
    expect(useAuthStore.getState().hasPermission('alert:read')).toBe(true);
  });

  it('shows an error on invalid credentials and stays on the login screen', async () => {
    vi.mocked(login).mockRejectedValue(new AuthError(401));
    renderLogin();

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'x' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'y' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/Invalid/));
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});
