import { zodResolver } from '@hookform/resolvers/zod';
import { UserPlus, Users as UsersIcon } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useAuthStore } from '@/auth/authStore';
import { ApiError } from '@/api/client';
import {
  Badge,
  Button,
  Input,
  PageHeader,
  Panel,
  PanelBody,
  PanelHeader,
  PanelTitle,
  StatusDot,
} from '@/design-system';
import { cn } from '@/lib/cn';
import { ROLES, type Role, type UserRecord, useUserActions, useUsers } from './api/usersApi';

/**
 * User Management (ARCHITECTURE.md §2 Screen 12, §4.1.3, §7.4). Live today — user-service is gateway-
 * routed. Accounts, their roles and effective permissions, with create / grant-role / revoke-role /
 * deactivate, each gated in the UI on the caller's own permissions (convenience) and enforced
 * authoritatively by the server, which refuses self-escalation and separation-of-duty violations and
 * audits every change fail-closed.
 */
export function UsersPage() {
  const users = useUsers();
  const perms = useAuthStore((s) => s.perms);
  const canWrite = perms.includes('user:write');
  const canRole = perms.includes('role:write');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const rows = users.data ?? [];
  const selected = useMemo(() => rows.find((u) => u.id === selectedId) ?? null, [rows, selectedId]);

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={UsersIcon} title="User Management">
        {canWrite && (
          <Button size="sm" variant="secondary" onClick={() => setCreating((v) => !v)}>
            <UserPlus className="size-3.5" /> New user
          </Button>
        )}
      </PageHeader>

      <div className="grid flex-1 grid-cols-1 gap-3 overflow-hidden p-3 lg:grid-cols-[320px_1fr]">
        <Panel className="flex min-h-0 flex-col">
          <PanelHeader>
            <PanelTitle>Accounts</PanelTitle>
            <span className="tnum text-2xs text-subtle-foreground">{rows.length}</span>
          </PanelHeader>
          <PanelBody className="min-h-0 flex-1 space-y-1 overflow-auto p-1.5">
            {users.isLoading ? (
              <p className="p-2 text-xs text-subtle-foreground">Loading…</p>
            ) : users.isError ? (
              <p className="p-2 text-xs text-warn">Could not load accounts.</p>
            ) : rows.length === 0 ? (
              <p className="p-2 text-xs text-subtle-foreground">No accounts.</p>
            ) : (
              rows.map((user) => (
                <button
                  key={user.id}
                  type="button"
                  onClick={() => setSelectedId(user.id)}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-sm border border-transparent px-2 py-1.5 text-left text-xs hover:bg-elevated/60',
                    user.id === selectedId && 'border-border-strong bg-elevated',
                  )}
                >
                  <StatusDot status={user.status === 'ACTIVE' ? 'ok' : 'down'} />
                  <span className="min-w-0 flex-1 truncate font-medium">{user.username}</span>
                  <span className="tnum text-subtle-foreground">{user.roles.length}r</span>
                </button>
              ))
            )}
          </PanelBody>
        </Panel>

        <div className="min-h-0 overflow-auto">
          {creating && canWrite ? (
            <CreateUserForm onClose={() => setCreating(false)} />
          ) : selected ? (
            <UserDetail user={selected} canRole={canRole} canWrite={canWrite} />
          ) : (
            <div className="grid h-full place-items-center text-center text-sm text-muted-foreground">
              Select an account to view its roles and permissions.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function UserDetail({ user, canRole, canWrite }: { user: UserRecord; canRole: boolean; canWrite: boolean }) {
  const { assignRole, revokeRole, deactivate } = useUserActions();
  const [role, setRole] = useState<Role>('VIEWER');
  const [justification, setJustification] = useState('');
  const error = firstError(assignRole.error, revokeRole.error, deactivate.error);
  const justificationOk = justification.trim().length >= 10;

  return (
    <div className="space-y-3">
      <Panel>
        <PanelHeader>
          <PanelTitle>{user.displayName || user.username}</PanelTitle>
          <Badge variant={user.status === 'ACTIVE' ? 'ok' : 'down'}>{user.status}</Badge>
        </PanelHeader>
        <PanelBody className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
          <Field label="Username"><span className="tnum">{user.username}</span></Field>
          <Field label="Email">{user.email}</Field>
          <Field label="Roles">
            <span className="flex flex-wrap gap-1">
              {user.roles.length === 0 ? '—' : user.roles.map((r) => <Badge key={r} variant="neutral">{r}</Badge>)}
            </span>
          </Field>
          <Field label="Permissions"><span className="tnum">{user.permissions.length}</span></Field>
        </PanelBody>
      </Panel>

      <Panel>
        <PanelHeader>
          <PanelTitle>Effective permissions</PanelTitle>
          <span className="tnum text-2xs text-subtle-foreground">{user.permissions.length}</span>
        </PanelHeader>
        <PanelBody className="flex flex-wrap gap-1">
          {user.permissions.length === 0 ? (
            <p className="text-xs text-subtle-foreground">None — no roles assigned.</p>
          ) : (
            user.permissions.map((p) => (
              <span key={p} className="tnum rounded-sm bg-elevated px-1.5 py-0.5 text-2xs text-muted-foreground">
                {p}
              </span>
            ))
          )}
        </PanelBody>
      </Panel>

      {canRole && (
        <Panel>
          <PanelHeader>
            <PanelTitle>Role change</PanelTitle>
          </PanelHeader>
          <PanelBody className="space-y-2">
            <div className="flex items-center gap-2">
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as Role)}
                aria-label="Role"
                className="rounded-sm border border-input bg-surface px-1.5 py-1 text-xs text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {ROLES.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
              <Button
                size="sm"
                variant="secondary"
                disabled={!justificationOk || assignRole.isPending}
                onClick={() => assignRole.mutate({ userId: user.id, role, justification })}
              >
                Grant
              </Button>
              <Button
                size="sm"
                variant="ghost"
                disabled={!justificationOk || revokeRole.isPending}
                onClick={() => revokeRole.mutate({ userId: user.id, role, justification })}
              >
                Revoke
              </Button>
            </div>
            <Input
              value={justification}
              onChange={(e) => setJustification(e.target.value)}
              placeholder="Justification (min 10 chars) — written to the audit log"
              aria-label="Justification"
              className="text-xs"
            />
            {error && <p className="text-2xs text-critical">{error}</p>}
          </PanelBody>
        </Panel>
      )}

      {canWrite && user.status === 'ACTIVE' && (
        <Button
          variant="outline"
          size="sm"
          disabled={deactivate.isPending}
          onClick={() => deactivate.mutate({ userId: user.id })}
        >
          Deactivate account
        </Button>
      )}
    </div>
  );
}

const createSchema = z.object({
  username: z.string().trim().regex(/^[a-z0-9._-]{3,64}$/, 'lowercase letters, digits, . _ - (3–64)'),
  email: z.string().trim().email(),
  displayName: z.string().trim().min(1).max(128),
});

function CreateUserForm({ onClose }: { onClose: () => void }) {
  const { create } = useUserActions();
  const form = useForm<z.infer<typeof createSchema>>({
    resolver: zodResolver(createSchema),
    defaultValues: { username: '', email: '', displayName: '' },
  });
  const error = create.error instanceof ApiError ? messageFor(create.error) : null;

  const onSubmit = form.handleSubmit((values) => {
    create.mutate(values, { onSuccess: onClose });
  });

  return (
    <Panel className="max-w-md">
      <PanelHeader>
        <PanelTitle>New account</PanelTitle>
      </PanelHeader>
      <PanelBody>
        <form onSubmit={onSubmit} className="space-y-2.5">
          <FormField label="Username" error={form.formState.errors.username?.message}>
            <Input {...form.register('username')} placeholder="k.ullah" className="text-xs" />
          </FormField>
          <FormField label="Email" error={form.formState.errors.email?.message}>
            <Input {...form.register('email')} placeholder="k.ullah@nust.edu.pk" className="text-xs" />
          </FormField>
          <FormField label="Display name" error={form.formState.errors.displayName?.message}>
            <Input {...form.register('displayName')} placeholder="Kaleem Ullah" className="text-xs" />
          </FormField>
          {error && <p className="text-2xs text-critical">{error}</p>}
          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" size="sm" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={create.isPending}>
              Create — no roles
            </Button>
          </div>
        </form>
      </PanelBody>
    </Panel>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-2xs uppercase tracking-wide text-subtle-foreground">{label}</dt>
      <dd className="text-foreground">{children}</dd>
    </div>
  );
}

function FormField({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-2xs uppercase tracking-wide text-subtle-foreground">{label}</label>
      {children}
      {error && <p className="text-2xs text-critical">{error}</p>}
    </div>
  );
}

function firstError(...errors: Array<unknown>): string | null {
  for (const error of errors) {
    if (error instanceof ApiError) {
      return messageFor(error);
    }
    if (error instanceof Error) {
      return error.message;
    }
  }
  return null;
}

function messageFor(error: ApiError): string {
  if (error.status === 403) {
    return 'Refused: you cannot grant authority you do not hold yourself (self-escalation denied).';
  }
  if (error.status === 409) {
    return 'Refused: this change would violate separation of duty.';
  }
  if (error.status === 400) {
    return 'The request was rejected as invalid.';
  }
  return `Request failed (${error.status}).`;
}
