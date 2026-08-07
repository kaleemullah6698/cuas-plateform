import { describe, expect, it } from 'vitest';
import { makeJwt } from '@/test/makeJwt';
import { decodeJwt } from './jwt';

describe('decodeJwt', () => {
  it('reads identity, roles, permissions and expiry from the payload', () => {
    const token = makeJwt({
      sub: 'usr-1',
      name: 'K. Ullah',
      roles: ['OPERATOR'],
      perms: ['alert:read', 'alert:acknowledge'],
      exp: 1_800_000_000,
    });

    const claims = decodeJwt(token);

    expect(claims).toEqual({
      sub: 'usr-1',
      name: 'K. Ullah',
      roles: ['OPERATOR'],
      perms: ['alert:read', 'alert:acknowledge'],
      expiresAtMs: 1_800_000_000_000,
    });
  });

  it('defaults missing claims safely and returns null for a malformed token', () => {
    const claims = decodeJwt(makeJwt({ sub: 'usr-2' }));
    expect(claims).toMatchObject({ sub: 'usr-2', name: null, roles: [], perms: [], expiresAtMs: 0 });

    expect(decodeJwt('not-a-jwt')).toBeNull();
  });
});
