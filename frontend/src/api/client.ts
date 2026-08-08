import { refresh } from '@/auth/authClient';
import { useAuthStore } from '@/auth/authStore';
import { config } from '@/config';

/**
 * The one HTTP client (add-frontend-feature skill; ARCHITECTURE.md §3.1). Every feature calls the
 * backend through here, so three cross-cutting concerns live in one place: the access-token header,
 * transparent **refresh-on-401** (the rotating token of §7.3, refreshed once and the request retried),
 * and a correlation id for end-to-end tracing (§8.6). Axios is deliberately not used.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem?: unknown,
  ) {
    super(`request failed (${status})`);
    this.name = 'ApiError';
  }
}

// A single in-flight refresh shared by every 401'd request, so a burst of calls triggers one refresh.
let refreshInFlight: Promise<boolean> | null = null;

async function refreshOnce(): Promise<boolean> {
  refreshInFlight ??= refresh()
    .then((result) => {
      if (result.kind === 'authenticated') {
        useAuthStore.getState().setSession(result.accessToken, result.sessionId);
        return true;
      }
      return false;
    })
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

async function request(path: string, init: RequestInit, retryOn401: boolean): Promise<Response> {
  const token = useAuthStore.getState().accessToken;
  const headers = new Headers(init.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (!headers.has('X-Correlation-Id')) {
    headers.set('X-Correlation-Id', crypto.randomUUID());
  }
  const response = await fetch(`${config.apiBaseUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  });
  if (response.status === 401 && retryOn401) {
    if (await refreshOnce()) {
      return request(path, init, false);
    }
    useAuthStore.getState().clear();
  }
  return response;
}

async function json<T>(path: string, init: RequestInit): Promise<T> {
  const response = await request(path, init, true);
  if (!response.ok) {
    throw new ApiError(response.status, await response.json().catch(() => undefined));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function withBody(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

/** Typed JSON helpers over the gateway. */
export const api = {
  get: <T>(path: string) => json<T>(path, { method: 'GET' }),
  post: <T>(path: string, body?: unknown) => json<T>(path, withBody('POST', body ?? {})),
  put: <T>(path: string, body: unknown) => json<T>(path, withBody('PUT', body)),
  patch: <T>(path: string, body: unknown) => json<T>(path, withBody('PATCH', body)),
  // DELETE optionally carries a body — a role revocation is a DELETE with its justification (§4.1.3).
  del: <T>(path: string, body?: unknown) =>
    json<T>(path, body === undefined ? { method: 'DELETE' } : withBody('DELETE', body)),
  /** Raw response, for downloads (a report artifact) where the body is not JSON. */
  raw: (path: string, init: RequestInit = {}) => request(path, init, true),
};
