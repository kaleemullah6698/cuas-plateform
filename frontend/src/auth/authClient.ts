import { config } from '@/config';

/** The outcome of a login: either issued tokens, or an MFA challenge to complete (§7.3, auth-service). */
export type LoginResult =
  | { kind: 'authenticated'; accessToken: string; expiresAt: string; sessionId: string }
  | { kind: 'mfaRequired'; challengeId: string };

/** A login or MFA failure, carrying the HTTP status so the form can word it (401 vs 423 locked). */
export class AuthError extends Error {
  constructor(readonly status: number) {
    super(`authentication failed (${status})`);
    this.name = 'AuthError';
  }
}

/**
 * Verifies a username and password against the gateway-proxied auth-service (§7.3). Sends credentials
 * so the rotating refresh token can be set as an HttpOnly cookie; the access token comes back in the
 * body and is held in memory only — never in {@code localStorage}.
 */
export async function login(username: string, password: string): Promise<LoginResult> {
  const body = await post('/api/v1/auth/login', { username, password });
  if (body.mfaRequired === true) {
    return { kind: 'mfaRequired', challengeId: String(body.challengeId) };
  }
  return authenticated(body);
}

/** Completes a login by verifying the second factor. */
export async function verifyMfa(challengeId: string, code: string): Promise<LoginResult> {
  return authenticated(await post('/api/v1/auth/mfa/verify', { challengeId, code }));
}

function authenticated(body: Record<string, unknown>): LoginResult {
  return {
    kind: 'authenticated',
    accessToken: String(body.accessToken),
    expiresAt: String(body.expiresAt),
    sessionId: String(body.sessionId),
  };
}

async function post(path: string, payload: unknown): Promise<Record<string, unknown>> {
  const response = await fetch(`${config.apiBaseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new AuthError(response.status);
  }
  return (await response.json()) as Record<string, unknown>;
}
