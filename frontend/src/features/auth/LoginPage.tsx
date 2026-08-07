import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRealtime } from '@/app/RealtimeContext';
import { AuthError, login, type LoginResult, verifyMfa } from '@/auth/authClient';
import { useAuthStore } from '@/auth/authStore';

/**
 * The login screen (ARCHITECTURE.md §2.4, §7.3). Verifies credentials against the gateway-proxied
 * auth-service, completes an MFA challenge when the account has a second factor, and on success stores
 * the access token in memory, opens the realtime connection, and enters the console. Deliberately thin
 * — this exists to reach the live stream, not to be the full identity surface.
 */
export function LoginPage() {
  const navigate = useNavigate();
  const realtime = useRealtime();
  const setSession = useAuthStore((state) => state.setSession);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function enter(result: Extract<LoginResult, { kind: 'authenticated' }>) {
    setSession(result.accessToken, result.sessionId);
    realtime.connect(result.accessToken);
    navigate('/ops/tracks');
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const result = challengeId
        ? await verifyMfa(challengeId, code)
        : await login(username, password);
      if (result.kind === 'mfaRequired') {
        setChallengeId(result.challengeId);
      } else {
        enter(result);
      }
    } catch (caught) {
      setError(messageFor(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main style={styles.screen}>
      <form onSubmit={onSubmit} style={styles.card} aria-label="Sign in">
        <h1 style={styles.title}>C-UAS Command &amp; Control</h1>
        {!challengeId ? (
          <>
            <label style={styles.label}>
              Username
              <input
                style={styles.input}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
              />
            </label>
            <label style={styles.label}>
              Password
              <input
                style={styles.input}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
          </>
        ) : (
          <label style={styles.label}>
            Authentication code
            <input
              style={styles.input}
              value={code}
              onChange={(e) => setCode(e.target.value)}
              inputMode="numeric"
              autoComplete="one-time-code"
              required
            />
          </label>
        )}
        {error && (
          <p role="alert" style={styles.error}>
            {error}
          </p>
        )}
        <button type="submit" style={styles.button} disabled={busy}>
          {busy ? 'Signing in…' : challengeId ? 'Verify' : 'Sign in'}
        </button>
      </form>
    </main>
  );
}

function messageFor(caught: unknown): string {
  if (caught instanceof AuthError) {
    if (caught.status === 401) {
      return 'Invalid username, password, or code.';
    }
    if (caught.status === 423) {
      return 'Account temporarily locked. Try again later.';
    }
  }
  return 'Sign-in failed. Please try again.';
}

const styles: Record<string, React.CSSProperties> = {
  screen: { display: 'grid', placeItems: 'center', height: '100%' },
  card: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
    width: '20rem',
    padding: '1.5rem',
    background: 'var(--panel)',
    border: '1px solid var(--border)',
    borderRadius: '0.5rem',
  },
  title: { margin: 0, fontSize: '1.05rem', color: 'var(--text)' },
  label: { display: 'flex', flexDirection: 'column', gap: '0.25rem', fontSize: '0.8rem', color: 'var(--text-dim)' },
  input: {
    padding: '0.5rem',
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    borderRadius: '0.25rem',
    color: 'var(--text)',
  },
  error: { color: 'var(--threat-critical)', fontSize: '0.8rem', margin: 0 },
  button: {
    marginTop: '0.5rem',
    padding: '0.55rem',
    background: 'var(--accent)',
    border: 'none',
    borderRadius: '0.25rem',
    color: 'white',
    cursor: 'pointer',
  },
};
