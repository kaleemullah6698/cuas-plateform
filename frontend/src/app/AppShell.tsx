import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '@/auth/authStore';
import { TooltipProvider } from '@/design-system';
import { NavRail } from './NavRail';
import { TopBar } from './TopBar';

/**
 * The authenticated command-centre layout (ARCHITECTURE.md §2.4, §3.3): a top bar, the primary nav
 * rail, and the routed workspace. Guards its routes — an unauthenticated visitor is sent to login, and
 * because the access token is memory-only (§7.3) a refreshed tab returns there rather than showing a
 * dead session. Full role-based route gating arrives with M10.B.
 */
export function AppShell() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return (
    <TooltipProvider delayDuration={200} skipDelayDuration={400}>
      <div className="flex h-full flex-col">
        <TopBar />
        <div className="flex min-h-0 flex-1">
          <NavRail />
          <main className="min-w-0 flex-1 overflow-hidden">
            <Outlet />
          </main>
        </div>
      </div>
    </TooltipProvider>
  );
}
