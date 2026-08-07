import { zodResolver } from '@hookform/resolvers/zod';
import { AlertTriangle, Radio } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useRealtime } from '@/app/RealtimeContext';
import { AuthError, login, type LoginResult, verifyMfa } from '@/auth/authClient';
import { useAuthStore } from '@/auth/authStore';
import { Button, Input, Label, Panel } from '@/design-system';

const credentialsSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
});
const mfaSchema = z.object({ code: z.string().min(6, 'Enter the 6-digit code') });

type Credentials = z.infer<typeof credentialsSchema>;
type MfaCode = z.infer<typeof mfaSchema>;

/**
 * The login screen (ARCHITECTURE.md §2.4, §7.3). Verifies credentials against the gateway-proxied
 * auth-service, steps up to MFA when the account has a second factor, and on success holds the access
 * token in memory only, opens the realtime stream, and enters the console. A calm, single-column
 * sign-in — the operator's first impression of a system that is precise, not busy.
 */
export function LoginPage() {
  const navigate = useNavigate();
  const realtime = useRealtime();
  const setSession = useAuthStore((state) => state.setSession);
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const credentials = useForm<Credentials>({ resolver: zodResolver(credentialsSchema) });
  const mfa = useForm<MfaCode>({ resolver: zodResolver(mfaSchema) });

  function enter(result: Extract<LoginResult, { kind: 'authenticated' }>) {
    setSession(result.accessToken, result.sessionId);
    realtime.connect(result.accessToken);
    navigate('/dashboard');
  }

  const submitCredentials = credentials.handleSubmit(async ({ username, password }) => {
    setError(null);
    try {
      const result = await login(username, password);
      if (result.kind === 'mfaRequired') {
        setChallengeId(result.challengeId);
      } else {
        enter(result);
      }
    } catch (caught) {
      setError(messageFor(caught));
    }
  });

  const submitMfa = mfa.handleSubmit(async ({ code }) => {
    setError(null);
    try {
      enter(await verifyMfa(challengeId!, code) as Extract<LoginResult, { kind: 'authenticated' }>);
    } catch (caught) {
      setError(messageFor(caught));
    }
  });

  const busy = credentials.formState.isSubmitting || mfa.formState.isSubmitting;

  return (
    <main className="grid h-full place-items-center bg-background p-4">
      <div className="w-full max-w-sm">
        <div className="mb-5 flex items-center gap-2.5">
          <div className="flex size-8 items-center justify-center rounded-md bg-primary/15 text-primary">
            <Radio className="size-4" />
          </div>
          <div className="leading-tight">
            <div className="text-base font-semibold tracking-tight">C-UAS Command</div>
            <div className="text-2xs uppercase tracking-wider text-subtle-foreground">
              Counter-UAS Operations
            </div>
          </div>
        </div>

        <Panel className="p-5">
          {!challengeId ? (
            <form onSubmit={submitCredentials} className="flex flex-col gap-3" aria-label="Sign in">
              <Field id="username" label="Username" error={credentials.formState.errors.username?.message}>
                <Input id="username" autoComplete="username" autoFocus {...credentials.register('username')} />
              </Field>
              <Field id="password" label="Password" error={credentials.formState.errors.password?.message}>
                <Input id="password" type="password" autoComplete="current-password" {...credentials.register('password')} />
              </Field>
              {error && <ErrorNote>{error}</ErrorNote>}
              <Button type="submit" size="lg" className="mt-1 w-full" loading={busy}>
                Sign in
              </Button>
            </form>
          ) : (
            <form onSubmit={submitMfa} className="flex flex-col gap-3" aria-label="Two-factor verification">
              <p className="text-xs text-muted-foreground">
                Enter the 6-digit code from your authenticator.
              </p>
              <Field id="code" label="Authentication code" error={mfa.formState.errors.code?.message}>
                <Input
                  id="code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  autoFocus
                  className="tnum tracking-[0.3em]"
                  {...mfa.register('code')}
                />
              </Field>
              {error && <ErrorNote>{error}</ErrorNote>}
              <Button type="submit" size="lg" className="mt-1 w-full" loading={busy}>
                Verify
              </Button>
            </form>
          )}
        </Panel>
      </div>
    </main>
  );
}

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error && (
        <span role="alert" className="text-2xs text-critical">
          {error}
        </span>
      )}
    </div>
  );
}

function ErrorNote({ children }: { children: React.ReactNode }) {
  return (
    <div
      role="alert"
      className="flex items-center gap-1.5 rounded-md border border-critical/40 bg-critical/10 px-2.5 py-1.5 text-xs text-critical"
    >
      <AlertTriangle className="size-3.5 shrink-0" />
      {children}
    </div>
  );
}

function messageFor(caught: unknown): string {
  if (caught instanceof AuthError) {
    if (caught.status === 401) return 'Invalid username, password, or code.';
    if (caught.status === 423) return 'Account temporarily locked. Try again later.';
  }
  return 'Sign-in failed. Please try again.';
}
