import { create } from 'zustand';
import { decodeJwt } from './jwt';

interface AuthState {
  accessToken: string | null;
  sessionId: string | null;
  isAuthenticated: boolean;
  /** Operator display name, from the token's `name` claim (falls back to the subject). */
  displayName: string;
  /** The §7.4 roles the token carries. */
  roles: string[];
  /** The §7.4 permissions the token carries — the source of truth for role-based UI. */
  perms: string[];
  /** Access-token expiry (epoch ms), for proactive silent refresh. */
  expiresAtMs: number;
  setSession: (accessToken: string, sessionId: string) => void;
  hasPermission: (permission: string) => boolean;
  clear: () => void;
}

const ANONYMOUS = {
  accessToken: null,
  sessionId: null,
  isAuthenticated: false,
  displayName: 'Operator',
  roles: [] as string[],
  perms: [] as string[],
  expiresAtMs: 0,
};

/**
 * The session's auth state (ARCHITECTURE.md §7.3, §7.4). The access token lives here in JavaScript
 * memory only — never {@code localStorage} or a script-readable cookie — so it cannot be exfiltrated
 * and vanishes when the tab closes; the rotating refresh token is a separate HttpOnly cookie. The
 * identity (name, roles, permissions) is decoded from the token so the UI can gate on {@code perms}
 * without a second call — but the gateway re-checks every request, so this is convenience, not control.
 */
export const useAuthStore = create<AuthState>((set, get) => ({
  ...ANONYMOUS,

  setSession: (accessToken, sessionId) => {
    const claims = decodeJwt(accessToken);
    set({
      accessToken,
      sessionId,
      isAuthenticated: true,
      displayName: claims?.name ?? claims?.sub ?? 'Operator',
      roles: claims?.roles ?? [],
      perms: claims?.perms ?? [],
      expiresAtMs: claims?.expiresAtMs ?? 0,
    });
  },

  hasPermission: (permission) => get().perms.includes(permission),

  clear: () => set({ ...ANONYMOUS }),
}));
