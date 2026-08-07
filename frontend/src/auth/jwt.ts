/**
 * Reads the claims out of an access token for the UI (ARCHITECTURE.md §7.3). The token is **not**
 * verified here — the gateway does that on every request; the client only decodes the payload to know
 * who the operator is and what to show. Role-based UI is convenience; the server is the control (§7.4).
 */
export interface JwtClaims {
  sub: string;
  name: string | null;
  roles: string[];
  perms: string[];
  /** Expiry, epoch milliseconds. */
  expiresAtMs: number;
}

export function decodeJwt(token: string): JwtClaims | null {
  const payload = token.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const json = base64UrlDecode(payload);
    const claims = JSON.parse(json) as Record<string, unknown>;
    return {
      sub: String(claims.sub ?? ''),
      name: typeof claims.name === 'string' && claims.name ? claims.name : null,
      roles: toStringArray(claims.roles),
      perms: toStringArray(claims.perms),
      expiresAtMs: typeof claims.exp === 'number' ? claims.exp * 1000 : 0,
    };
  } catch {
    return null;
  }
}

function toStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [];
}

function base64UrlDecode(input: string): string {
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64);
  // Decode as UTF-8 so a name with non-ASCII characters survives.
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
