import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, type ReactElement, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/app/AppShell';
import { queryClient } from '@/app/queryClient';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { SessionManager } from '@/auth/SessionManager';
import { RequirePermission } from '@/auth/permissions';
import { Spinner } from '@/design-system';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { TracksPage } from '@/features/tracks/TracksPage';
import { AlertCenterPage } from '@/features/alerts/AlertCenterPage';
import { IncidentsPage } from '@/features/incidents/IncidentsPage';
import { ReportsPage } from '@/features/reports/ReportsPage';
import { ConfigPage } from '@/features/config/ConfigPage';
import { UsersPage } from '@/features/users/UsersPage';
import { AuditPage } from '@/features/audit/AuditPage';
import { NotificationCenterPage } from '@/features/notifications/NotificationCenterPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { ProfilePage } from '@/features/profile/ProfilePage';
import { SensorHealthPage } from '@/features/sensors/SensorHealthPage';
import { AiServicesPage } from '@/features/admin/ai-services/AiServicesPage';

// MapLibre GL is ~200 KB gzip — far larger than the rest of the console. The map route is code-split
// so that weight loads only when an operator opens the map, keeping the initial bundle within the §3.6
// budget the other screens respect.
const MapPage = lazy(() =>
  import('@/features/map/MapPage').then((module) => ({ default: module.MapPage })),
);

/**
 * The operator console route tree (ARCHITECTURE.md §3.6). Login is public; everything else lives
 * inside the authenticated {@link AppShell} and behind a {@link RequirePermission} guard — an operator
 * who reaches a URL their role does not grant gets a clear 403, not a blank screen. {@link
 * SessionManager} restores a session from the refresh cookie before the tree renders. Feature modules
 * land group by group (M10.C–G); until each arrives its route renders the real module header.
 */
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RealtimeProvider>
        <SessionManager>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AppShell />}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={guard(null, <DashboardPage />)} />
              <Route path="/map" element={guard('map:read', <LazyRoute><MapPage /></LazyRoute>)} />
              <Route path="/tracks" element={guard('track:read', <TracksPage />)} />
              <Route path="/sensors" element={guard('sensor:read', <SensorHealthPage />)} />
              <Route path="/alerts" element={guard('alert:read', <AlertCenterPage />)} />
              <Route path="/incidents" element={guard('incident:read', <IncidentsPage />)} />
              <Route path="/reports" element={guard('report:read', <ReportsPage />)} />
              <Route path="/config" element={guard('policy:read', <ConfigPage />)} />
              <Route path="/ai-services" element={guard('ai:configure', <AiServicesPage />)} />
              <Route path="/users" element={guard('user:read', <UsersPage />)} />
              <Route path="/audit" element={guard('audit:read', <AuditPage />)} />
              <Route path="/notifications" element={guard(null, <NotificationCenterPage />)} />
              <Route path="/profile" element={<ProfilePage />} />
            </Route>
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </BrowserRouter>
        </SessionManager>
      </RealtimeProvider>
    </QueryClientProvider>
  );
}

function guard(permission: string | null, element: ReactElement): ReactElement {
  return <RequirePermission permission={permission}>{element}</RequirePermission>;
}

/** Suspense boundary for a code-split route, with a centred spinner while the chunk loads. */
function LazyRoute({ children }: { children: ReactElement }): ReactElement {
  return (
    <Suspense
      fallback={
        <div className="grid h-full place-items-center">
          <Spinner />
        </div>
      }
    >
      {children}
    </Suspense>
  );
}
