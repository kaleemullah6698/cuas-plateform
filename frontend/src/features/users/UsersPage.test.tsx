// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/auth/authStore';
import { UsersPage } from './UsersPage';
import type { UserRecord } from './api/usersApi';

const USER: UserRecord = {
  id: 'u1',
  username: 'k.ullah',
  email: 'k.ullah@nust.edu.pk',
  displayName: 'Kaleem Ullah',
  status: 'ACTIVE',
  roles: ['OPERATOR'],
  permissions: ['alert:read', 'track:read'],
  createdAt: '2026-08-01T10:00:00Z',
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <UsersPage />
    </QueryClientProvider>,
  );
}

describe('UsersPage (live — user-service is gateway-routed)', () => {
  beforeEach(() => {
    useAuthStore.setState({ perms: ['user:read', 'user:write', 'role:write'], isAuthenticated: true });
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [USER] })));
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    useAuthStore.getState().clear();
  });

  it('lists accounts and shows a selected account with role controls when permitted', async () => {
    renderPage();
    const account = await screen.findByText('k.ullah');
    account.click();
    // Detail shows roles and effective permissions.
    expect(await screen.findByText('Effective permissions')).toBeInTheDocument();
    expect(screen.getByText('alert:read')).toBeInTheDocument();
    // role:write is held, so the grant/revoke controls are present.
    expect(screen.getByRole('button', { name: 'Grant' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revoke' })).toBeInTheDocument();
  });
});
