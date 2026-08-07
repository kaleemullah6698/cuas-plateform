import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthError, login, verifyMfa } from './authClient';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('authClient', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('returns tokens on a successful login', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      jsonResponse({ accessToken: 'jwt', expiresAt: '2026-08-07T00:00:00Z', sessionId: 'sess' })));

    const result = await login('kullah', 'secret');

    expect(result).toEqual({
      kind: 'authenticated',
      accessToken: 'jwt',
      expiresAt: '2026-08-07T00:00:00Z',
      sessionId: 'sess',
    });
  });

  it('returns an MFA challenge when the account has a second factor', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      jsonResponse({ mfaRequired: true, challengeId: 'chal-1' })));

    expect(await login('kullah', 'secret')).toEqual({ kind: 'mfaRequired', challengeId: 'chal-1' });
  });

  it('completes an MFA challenge into tokens', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      jsonResponse({ accessToken: 'jwt2', expiresAt: '2026', sessionId: 's2' })));

    const result = await verifyMfa('chal-1', '123456');

    expect(result.kind).toBe('authenticated');
  });

  it('throws an AuthError carrying the status on failure', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 401 })));

    await expect(login('kullah', 'wrong')).rejects.toBeInstanceOf(AuthError);
    await expect(login('kullah', 'wrong')).rejects.toHaveProperty('status', 401);
  });
});
