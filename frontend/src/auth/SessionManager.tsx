import { Radio } from 'lucide-react';
import { type ReactNode, useEffect, useState } from 'react';
import { useRealtime } from '@/app/RealtimeContext';
import { Spinner } from '@/design-system';
import { refresh } from './authClient';
import { useAuthStore } from './authStore';

/** Refresh this long before the access token expires, so a call never rides an expired token. */
const REFRESH_LEAD_MS = 60_000;

/**
 * Owns the session lifecycle (ARCHITECTURE.md §7.3). Two duties:
 *
 * <p><b>Restore on load.</b> The access token is memory-only, so a page refresh loses it — but the
 * rotating refresh token is a 7-day HttpOnly cookie. On mount the manager tries one refresh; if the
 * cookie is still valid the session (and the live stream) come back without a re-login, and if not it
 * falls through to the login screen. The app holds a brief splash until that attempt resolves, so a
 * returning operator never sees a flash of the login page.
 *
 * <p><b>Silent refresh.</b> A minute before the 15-minute access token expires it rotates the pair in
 * the background and updates the stream's token, so an operator on a long shift is never interrupted.
 * A failed refresh (revoked family, §7.3) ends the session cleanly.
 */
export function SessionManager({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const realtime = useRealtime();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const expiresAtMs = useAuthStore((state) => state.expiresAtMs);

  useEffect(() => {
    let active = true;
    refresh()
      .then((result) => {
        if (active && result.kind === 'authenticated') {
          useAuthStore.getState().setSession(result.accessToken, result.sessionId);
          realtime.connect(result.accessToken);
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) setReady(true);
      });
    return () => {
      active = false;
    };
  }, [realtime]);

  useEffect(() => {
    if (!isAuthenticated || !expiresAtMs) {
      return;
    }
    const delay = Math.max(0, expiresAtMs - Date.now() - REFRESH_LEAD_MS);
    const timer = window.setTimeout(async () => {
      try {
        const result = await refresh();
        if (result.kind === 'authenticated') {
          useAuthStore.getState().setSession(result.accessToken, result.sessionId);
          realtime.updateToken(result.accessToken);
        } else {
          useAuthStore.getState().clear();
        }
      } catch {
        useAuthStore.getState().clear();
        realtime.disconnect();
      }
    }, delay);
    return () => window.clearTimeout(timer);
  }, [isAuthenticated, expiresAtMs, realtime]);

  if (!ready) {
    return <Splash />;
  }
  return <>{children}</>;
}

function Splash() {
  return (
    <div className="grid h-full place-items-center">
      <div className="flex flex-col items-center gap-3 text-muted-foreground">
        <div className="flex size-9 items-center justify-center rounded-md bg-primary/15 text-primary">
          <Radio className="size-5" />
        </div>
        <Spinner className="size-4" />
        <span className="text-xs">Restoring session…</span>
      </div>
    </div>
  );
}
