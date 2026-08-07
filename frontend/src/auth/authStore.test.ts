import { beforeEach, describe, expect, it } from 'vitest';
import { makeJwt } from '@/test/makeJwt';
import { useAuthStore } from './authStore';

describe('authStore', () => {
  beforeEach(() => useAuthStore.getState().clear());

  it('decodes identity and permissions out of the access token on setSession', () => {
    const token = makeJwt({ sub: 'usr-1', name: 'K. Ullah', roles: ['ANALYST'], perms: ['report:read'] });

    useAuthStore.getState().setSession(token, 'sess-1');
    const state = useAuthStore.getState();

    expect(state.isAuthenticated).toBe(true);
    expect(state.displayName).toBe('K. Ullah');
    expect(state.roles).toEqual(['ANALYST']);
    expect(state.hasPermission('report:read')).toBe(true);
    expect(state.hasPermission('report:generate')).toBe(false);
  });

  it('falls back to the subject when the token has no name', () => {
    useAuthStore.getState().setSession(makeJwt({ sub: 'usr-9' }), 'sess');
    expect(useAuthStore.getState().displayName).toBe('usr-9');
  });

  it('clears the whole identity on sign-out', () => {
    useAuthStore.getState().setSession(makeJwt({ sub: 'u', perms: ['x'] }), 's');
    useAuthStore.getState().clear();
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.perms).toEqual([]);
    expect(state.accessToken).toBeNull();
  });
});
