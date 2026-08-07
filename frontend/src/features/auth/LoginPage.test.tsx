// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { AuthError, login } from '@/auth/authClient';
import { useAuthStore } from '@/auth/authStore';
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
          <Route path="/ops/tracks" element={<div>TRACK CONSOLE</div>} />
        </Routes>
      </MemoryRouter>
    </RealtimeProvider>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => useAuthStore.getState().clear());

  it('signs in and enters the console on valid credentials', async () => {
    vi.mocked(login).mockResolvedValue({
      kind: 'authenticated',
      accessToken: 'jwt',
      expiresAt: 'x',
      sessionId: 'sess',
    });
    renderLogin();

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'kullah' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('TRACK CONSOLE')).toBeInTheDocument(); // navigated on success
    expect(useAuthStore.getState().accessToken).toBe('jwt');
  });

  it('shows an error message on invalid credentials', async () => {
    vi.mocked(login).mockRejectedValue(new AuthError(401));
    renderLogin();

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'x' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'y' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Invalid/);
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});
