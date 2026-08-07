import { create } from 'zustand';

interface AuthState {
  accessToken: string | null;
  sessionId: string | null;
  isAuthenticated: boolean;
  /** Operator display name — enriched from the decoded JWT in M10.B; a safe default until then. */
  displayName: string;
  setSession: (accessToken: string, sessionId: string) => void;
  clear: () => void;
}

/**
 * The session's auth state (ARCHITECTURE.md §7.3). The access token lives here, in JavaScript memory
 * only — never {@code localStorage} or a readable cookie — so it cannot be exfiltrated by script that
 * reaches storage, and it simply vanishes when the tab closes. The rotating refresh token is a
 * separate HttpOnly cookie the browser never exposes to script.
 */
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  sessionId: null,
  isAuthenticated: false,
  displayName: 'Operator',
  setSession: (accessToken, sessionId) => set({ accessToken, sessionId, isAuthenticated: true }),
  clear: () =>
    set({ accessToken: null, sessionId: null, isAuthenticated: false, displayName: 'Operator' }),
}));
