import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { RealtimeProvider } from '@/app/RealtimeContext';
import { ProtectedShell } from '@/app/ProtectedShell';
import { LoginPage } from '@/features/auth/LoginPage';
import { TrackConsole } from '@/features/tracks/TrackConsole';

/**
 * The thin M9 console (ARCHITECTURE.md §3.6, scoped down): a login route, and one protected route —
 * the live track list, framed by the shell's connection banner and alert strip. The full route tree
 * (map, timeline, sensors, incidents, audit, reports, admin) is deferred to a later frontend phase;
 * this exists to verify the backend and the stream end to end.
 */
export default function App() {
  return (
    <RealtimeProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedShell />}>
            <Route path="/ops/tracks" element={<TrackConsole />} />
            <Route index element={<Navigate to="/ops/tracks" replace />} />
          </Route>
          <Route path="*" element={<Navigate to="/ops/tracks" replace />} />
        </Routes>
      </BrowserRouter>
    </RealtimeProvider>
  );
}
