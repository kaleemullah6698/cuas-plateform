/** Builds an unsigned JWT string with the given claims — enough to exercise the client-side decode. */
export function makeJwt(claims: Record<string, unknown>): string {
  const segment = (value: unknown) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${segment({ alg: 'RS256', typ: 'JWT' })}.${segment(claims)}.signature`;
}
