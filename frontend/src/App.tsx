import {
  Bell,
  FileText,
  Map,
  Radar,
  ScrollText,
  ShieldAlert,
  SlidersHorizontal,
  Users,
} from 'lucide-react';
import type { ReactElement } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/app/AppShell';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { SessionManager } from '@/auth/SessionManager';
import { RequirePermission } from '@/auth/permissions';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { ProfilePage } from '@/features/profile/ProfilePage';
import { ModulePlaceholder } from '@/pages/ModulePlaceholder';

/**
 * The operator console route tree (ARCHITECTURE.md §3.6). Login is public; everything else lives
 * inside the authenticated {@link AppShell} and behind a {@link RequirePermission} guard — an operator
 * who reaches a URL their role does not grant gets a clear 403, not a blank screen. {@link
 * SessionManager} restores a session from the refresh cookie before the tree renders. Feature modules
 * land group by group (M10.C–G); until each arrives its route renders the real module header.
 */
export default function App() {
  return (
    <RealtimeProvider>
      <SessionManager>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AppShell />}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={guard(null, <DashboardPage />)} />
              <Route path="/map" element={guard('map:read',
                <ModulePlaceholder title="Live Airspace Map" icon={Map} milestone="M10.D"
                  description="Tracks, sensors, protected zones, alerts and predicted drone paths on a live map." />)} />
              <Route path="/tracks" element={guard('track:read',
                <ModulePlaceholder title="Track Console" icon={Radar} milestone="M10.E"
                  description="The live track list and full track detail — classification, telemetry, prediction." />)} />
              <Route path="/alerts" element={guard('alert:read',
                <ModulePlaceholder title="Alert Center" icon={Bell} milestone="M10.F"
                  description="The operator alert queue and the acknowledge → assign → resolve workflow." />)} />
              <Route path="/incidents" element={guard('incident:read',
                <ModulePlaceholder title="Incidents" icon={ShieldAlert} milestone="M10.F"
                  description="Operator-declared investigations with timelines, notes and evidence bundles." />)} />
              <Route path="/reports" element={guard('report:read',
                <ModulePlaceholder title="Reports" icon={FileText} milestone="M10.G"
                  description="Generate and download incident, summary, sensor and fusion reports (PDF/JSON/CSV)." />)} />
              <Route path="/config" element={guard('policy:read',
                <ModulePlaceholder title="Configuration" icon={SlidersHorizontal} milestone="M10.G"
                  description="Runtime configuration namespaces with version history and rollback." />)} />
              <Route path="/users" element={guard('user:read',
                <ModulePlaceholder title="User Management" icon={Users} milestone="M10.G"
                  description="Operators, roles and permissions." />)} />
              <Route path="/audit" element={guard('audit:read',
                <ModulePlaceholder title="Audit" icon={ScrollText} milestone="M10.G"
                  description="The tamper-evident audit log and chain verification." />)} />
              <Route path="/profile" element={<ProfilePage />} />
            </Route>
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </BrowserRouter>
      </SessionManager>
    </RealtimeProvider>
  );
}

function guard(permission: string | null, element: ReactElement): ReactElement {
  return <RequirePermission permission={permission}>{element}</RequirePermission>;
}
