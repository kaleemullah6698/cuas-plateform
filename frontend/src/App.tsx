import {
  Bell,
  FileText,
  LayoutDashboard,
  Map,
  Radar,
  ScrollText,
  ShieldAlert,
  SlidersHorizontal,
  User,
  Users,
} from 'lucide-react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/app/AppShell';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { LoginPage } from '@/features/auth/LoginPage';
import { ModulePlaceholder } from '@/pages/ModulePlaceholder';

/**
 * The operator console route tree (ARCHITECTURE.md §3.6). Login is public; everything else lives
 * inside the authenticated {@link AppShell}. Feature modules land group by group (M10.C–G); until each
 * arrives its route renders the real module header so the shell is whole and navigable.
 */
export default function App() {
  return (
    <RealtimeProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route
              path="/dashboard"
              element={
                <ModulePlaceholder
                  title="Operations Dashboard"
                  description="The command-centre overview — live threat picture, sensor coverage, and activity."
                  icon={LayoutDashboard}
                  milestone="M10.C"
                />
              }
            />
            <Route
              path="/map"
              element={
                <ModulePlaceholder
                  title="Live Airspace Map"
                  description="Tracks, sensors, protected zones, alerts and predicted drone paths on a live map."
                  icon={Map}
                  milestone="M10.D"
                />
              }
            />
            <Route
              path="/tracks"
              element={
                <ModulePlaceholder
                  title="Track Console"
                  description="The live track list and full track detail — classification, telemetry, prediction."
                  icon={Radar}
                  milestone="M10.E"
                />
              }
            />
            <Route
              path="/alerts"
              element={
                <ModulePlaceholder
                  title="Alert Center"
                  description="The operator alert queue and the acknowledge → assign → resolve workflow."
                  icon={Bell}
                  milestone="M10.F"
                />
              }
            />
            <Route
              path="/incidents"
              element={
                <ModulePlaceholder
                  title="Incidents"
                  description="Operator-declared investigations with timelines, notes and evidence bundles."
                  icon={ShieldAlert}
                  milestone="M10.F"
                />
              }
            />
            <Route
              path="/reports"
              element={
                <ModulePlaceholder
                  title="Reports"
                  description="Generate and download incident, summary, sensor and fusion reports (PDF/JSON/CSV)."
                  icon={FileText}
                  milestone="M10.G"
                />
              }
            />
            <Route
              path="/config"
              element={
                <ModulePlaceholder
                  title="Configuration"
                  description="Runtime configuration namespaces with version history and rollback."
                  icon={SlidersHorizontal}
                  milestone="M10.G"
                />
              }
            />
            <Route
              path="/users"
              element={
                <ModulePlaceholder
                  title="User Management"
                  description="Operators, roles and permissions."
                  icon={Users}
                  milestone="M10.G"
                />
              }
            />
            <Route
              path="/audit"
              element={
                <ModulePlaceholder
                  title="Audit"
                  description="The tamper-evident audit log and chain verification."
                  icon={ScrollText}
                  milestone="M10.G"
                />
              }
            />
            <Route
              path="/profile"
              element={
                <ModulePlaceholder
                  title="Profile"
                  description="Your account, notification preferences and sessions."
                  icon={User}
                  milestone="M10.B"
                />
              }
            />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </RealtimeProvider>
  );
}
