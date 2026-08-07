import { formatDistanceToNowStrict } from 'date-fns';
import { LogOut, User } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useRealtime } from '@/app/RealtimeContext';
import { logout } from '@/auth/authClient';
import { useAuthStore } from '@/auth/authStore';
import { Avatar, Badge, Button, Panel, PanelBody, PanelHeader, PanelTitle } from '@/design-system';

/**
 * The operator's account (ARCHITECTURE.md §2.4, §7.3/§7.4): identity, the roles and permissions the
 * session carries, session facts, and sign-out. The permission list is shown in full and verbatim —
 * an operator (and an auditor over their shoulder) should be able to see exactly what this session can
 * do, with no ambiguity.
 */
export function ProfilePage() {
  const navigate = useNavigate();
  const realtime = useRealtime();
  const { displayName, roles, perms, sessionId, expiresAtMs } = useAuthStore();

  async function signOut() {
    await logout();
    realtime.disconnect();
    useAuthStore.getState().clear();
    navigate('/login');
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-4">
        <User className="size-4 text-muted-foreground" strokeWidth={1.75} />
        <h1 className="text-sm font-semibold">Profile</h1>
      </div>

      <div className="grid flex-1 gap-4 overflow-auto p-4 lg:grid-cols-2">
        <Panel>
          <PanelHeader>
            <PanelTitle>Identity</PanelTitle>
          </PanelHeader>
          <PanelBody className="space-y-4">
            <div className="flex items-center gap-3">
              <Avatar name={displayName} className="size-10 text-sm" />
              <div>
                <div className="text-sm font-semibold">{displayName}</div>
                <div className="text-xs text-muted-foreground">
                  {roles.length ? roles.join(' · ') : 'No roles assigned'}
                </div>
              </div>
            </div>
            <div>
              <div className="mb-1.5 text-2xs font-semibold uppercase tracking-wider text-subtle-foreground">
                Roles
              </div>
              <div className="flex flex-wrap gap-1">
                {roles.length ? (
                  roles.map((role) => (
                    <Badge key={role} variant="primary">
                      {role}
                    </Badge>
                  ))
                ) : (
                  <span className="text-xs text-muted-foreground">——</span>
                )}
              </div>
            </div>
          </PanelBody>
        </Panel>

        <Panel>
          <PanelHeader>
            <PanelTitle>Session</PanelTitle>
          </PanelHeader>
          <PanelBody className="space-y-2 text-sm">
            <Row label="Session ID">
              <span className="tnum text-xs text-muted-foreground">{sessionId ?? '——'}</span>
            </Row>
            <Row label="Access token">
              <span className="text-xs text-muted-foreground">
                {expiresAtMs
                  ? `expires ${formatDistanceToNowStrict(expiresAtMs, { addSuffix: true })}`
                  : '——'}
              </span>
            </Row>
            <Row label="Permissions held">
              <span className="tnum text-xs text-muted-foreground">{perms.length}</span>
            </Row>
            <div className="pt-2">
              <Button variant="outline" size="sm" onClick={signOut}>
                <LogOut className="size-3.5" /> Sign out
              </Button>
            </div>
          </PanelBody>
        </Panel>

        <Panel className="lg:col-span-2">
          <PanelHeader>
            <PanelTitle>Permissions</PanelTitle>
          </PanelHeader>
          <PanelBody>
            {perms.length ? (
              <div className="grid gap-1 sm:grid-cols-2 lg:grid-cols-3">
                {[...perms].sort().map((perm) => (
                  <code
                    key={perm}
                    className="rounded-sm bg-surface px-2 py-1 text-xs text-muted-foreground"
                  >
                    {perm}
                  </code>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">This session carries no permissions.</p>
            )}
          </PanelBody>
        </Panel>
      </div>
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      {children}
    </div>
  );
}
