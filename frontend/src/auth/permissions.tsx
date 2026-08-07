import type { ReactElement, ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { ShieldOff } from 'lucide-react';
import { useAuthStore } from './authStore';

/** Whether the current operator holds a permission (§7.4). {@code null} means "any authenticated user". */
export function useHasPermission(permission: string | null): boolean {
  return useAuthStore((state) => permission === null || state.perms.includes(permission));
}

export function usePermissions(): string[] {
  return useAuthStore((state) => state.perms);
}

/**
 * Renders its children only if the operator holds the permission — the building block of role-based UI
 * (§7.4). Hiding a control the user cannot use is a courtesy; the server still enforces it, so this is
 * never the security boundary.
 */
export function Can({
  permission,
  children,
  fallback = null,
}: {
  permission: string;
  children: ReactNode;
  fallback?: ReactNode;
}) {
  return <>{useHasPermission(permission) ? children : fallback}</>;
}

/** Guards a route: unauthenticated → login; authenticated but unpermitted → a clear 403, not a blank. */
export function RequirePermission({
  permission,
  children,
}: {
  permission: string | null;
  children: ReactElement;
}) {
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const allowed = useHasPermission(permission);
  if (!authenticated) {
    return <Navigate to="/login" replace />;
  }
  if (!allowed) {
    return <Forbidden permission={permission} />;
  }
  return children;
}

function Forbidden({ permission }: { permission: string | null }) {
  return (
    <div className="grid h-full place-items-center p-6">
      <div className="max-w-sm text-center">
        <ShieldOff className="mx-auto size-8 text-subtle-foreground" strokeWidth={1.5} />
        <h1 className="mt-3 text-sm font-semibold">Access restricted</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Your role does not grant access to this area.
        </p>
        {permission && (
          <p className="mt-2 text-2xs uppercase tracking-wider text-subtle-foreground">
            Requires <span className="tnum">{permission}</span>
          </p>
        )}
      </div>
    </div>
  );
}
