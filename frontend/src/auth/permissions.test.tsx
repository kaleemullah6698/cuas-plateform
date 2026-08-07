// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { makeJwt } from '@/test/makeJwt';
import { useAuthStore } from './authStore';
import { Can, RequirePermission } from './permissions';

function authAs(perms: string[]) {
  useAuthStore.getState().setSession(makeJwt({ sub: 'u', perms }), 'sess');
}

describe('RBAC UI gates', () => {
  beforeEach(() => useAuthStore.getState().clear());

  it('Can shows content only when the permission is held', () => {
    authAs(['alert:acknowledge']);
    render(
      <>
        <Can permission="alert:acknowledge">
          <button>Acknowledge</button>
        </Can>
        <Can permission="alert:resolve" fallback={<span>no-resolve</span>}>
          <button>Resolve</button>
        </Can>
      </>,
    );
    expect(screen.getByRole('button', { name: 'Acknowledge' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Resolve' })).not.toBeInTheDocument();
    expect(screen.getByText('no-resolve')).toBeInTheDocument();
  });

  it('RequirePermission renders, forbids, or redirects to login', () => {
    function renderGuarded() {
      return render(
        <MemoryRouter initialEntries={['/reports']}>
          <Routes>
            <Route path="/login" element={<div>LOGIN</div>} />
            <Route
              path="/reports"
              element={
                <RequirePermission permission="report:read">
                  <div>REPORTS</div>
                </RequirePermission>
              }
            />
          </Routes>
        </MemoryRouter>,
      );
    }

    // Unauthenticated → login
    const { unmount } = renderGuarded();
    expect(screen.getByText('LOGIN')).toBeInTheDocument();
    unmount();

    // Authenticated without the permission → 403, not the content
    authAs(['track:read']);
    const denied = renderGuarded();
    expect(screen.getByText('Access restricted')).toBeInTheDocument();
    expect(screen.queryByText('REPORTS')).not.toBeInTheDocument();
    denied.unmount();

    // Authenticated with the permission → content
    authAs(['report:read']);
    renderGuarded();
    expect(screen.getByText('REPORTS')).toBeInTheDocument();
  });
});
