import { formatDistanceToNowStrict } from 'date-fns';
import { Bell, LayoutDashboard, Radar, Radio, ShieldAlert } from 'lucide-react';
import { useMemo } from 'react';
import { useRealtime } from '@/app/RealtimeContext';
import {
  KpiTile,
  Panel,
  PanelBody,
  PanelHeader,
  PanelTitle,
  PageHeader,
  StatusDot,
  ThreatIndicator,
} from '@/design-system';
import { cn } from '@/lib/cn';
import { SEVERITY_ORDER, type Severity, severityOf } from '@/lib/severity';
import type { AlertView, SensorView } from '@/realtime/protocol';
import { useAlerts, useConnection, useSensors, useTracks } from '@/realtime/hooks';
import { severityCounts, sensorCounts, worstAlertSeverity } from './metrics';

/**
 * The operations dashboard (ARCHITECTURE.md §2.4.1): the command-centre overview an operator lands on
 * — the live threat picture, sensor coverage, and the most recent alerts. Everything is derived from
 * the real-time stores, so it moves with the airspace without a single poll. The authoritative alert
 * queue (open vs resolved) lives in the Alert Center; here the picture is what is happening now.
 */
export function DashboardPage() {
  const realtime = useRealtime();
  const tracks = useTracks(realtime.tracks);
  const alerts = useAlerts(realtime.alerts);
  const sensors = useSensors(realtime.sensors);
  const connection = useConnection(realtime.connection);

  const counts = useMemo(() => severityCounts(alerts), [alerts]);
  const worst = worstAlertSeverity(alerts);
  const sensorStats = useMemo(() => sensorCounts(sensors), [sensors]);

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={LayoutDashboard} title="Operations Dashboard">
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <StatusDot
            status={connection.status === 'CONNECTED' ? 'ok' : 'down'}
            pulse={connection.status === 'CONNECTED'}
          />
          {connection.status === 'CONNECTED' ? 'Live' : 'Offline'}
        </span>
      </PageHeader>

      <div className="flex-1 space-y-4 overflow-auto p-4">
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <KpiTile label="Active Tracks" value={tracks.length} icon={Radar} />
          <KpiTile
            label="Live Alerts"
            value={alerts.length}
            icon={Bell}
            sub={worst ? <ThreatIndicator severity={worst} /> : 'clear'}
          />
          <KpiTile
            label="Sensors Online"
            value={`${sensorStats.online}/${sensorStats.total || 0}`}
            icon={Radio}
            sub={
              sensorStats.degraded + sensorStats.offline > 0
                ? `${sensorStats.degraded} degraded · ${sensorStats.offline} offline`
                : 'all nominal'
            }
          />
          <KpiTile
            label="Highest Threat"
            icon={ShieldAlert}
            value={worst ? <ThreatIndicator severity={worst} /> : <span className="text-muted-foreground">NONE</span>}
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          <ThreatPicture counts={counts} total={alerts.length} className="lg:col-span-2" />
          <SensorCoverage sensors={sensors} online={sensorStats.online} total={sensorStats.total} />
        </div>

        <RecentAlerts alerts={alerts} />
      </div>
    </div>
  );
}

function ThreatPicture({
  counts,
  total,
  className,
}: {
  counts: Record<Severity, number>;
  total: number;
  className?: string;
}) {
  const max = Math.max(1, ...SEVERITY_ORDER.map((s) => counts[s]));
  return (
    <Panel className={className}>
      <PanelHeader>
        <PanelTitle>Threat Picture</PanelTitle>
        <span className="tnum text-2xs text-subtle-foreground">{total} active</span>
      </PanelHeader>
      <PanelBody className="space-y-2">
        {total === 0 ? (
          <Empty>No active alerts — airspace clear.</Empty>
        ) : (
          SEVERITY_ORDER.map((severity) => (
            <div key={severity} className="flex items-center gap-3">
              <div className="w-16 shrink-0">
                <ThreatIndicator severity={severity} />
              </div>
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-elevated">
                <div
                  className={cn('h-full rounded-full', barColor[severity])}
                  style={{ width: `${(counts[severity] / max) * 100}%` }}
                />
              </div>
              <span className="tnum w-8 text-right text-sm text-muted-foreground">
                {counts[severity]}
              </span>
            </div>
          ))
        )}
      </PanelBody>
    </Panel>
  );
}

function SensorCoverage({
  sensors,
  online,
  total,
}: {
  sensors: readonly SensorView[];
  online: number;
  total: number;
}) {
  const impaired = sensors.filter((s) => !s.status.toUpperCase().includes('ONLINE'));
  return (
    <Panel>
      <PanelHeader>
        <PanelTitle>Sensor Coverage</PanelTitle>
        <span className="tnum text-2xs text-subtle-foreground">
          {online}/{total || 0}
        </span>
      </PanelHeader>
      <PanelBody className="space-y-2">
        {total === 0 ? (
          <Empty>Awaiting sensor telemetry.</Empty>
        ) : impaired.length === 0 ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <StatusDot status="ok" /> All sensors nominal
          </div>
        ) : (
          impaired.map((sensor) => (
            <div key={sensor.sensorId} className="flex items-center justify-between gap-2 text-sm">
              <span className="flex items-center gap-2">
                <StatusDot status={sensor.status.toUpperCase().includes('OFFLINE') ? 'down' : 'warn'} />
                <span className="font-medium">{sensor.sensorId}</span>
              </span>
              <span className="text-2xs uppercase tracking-wide text-muted-foreground">
                {sensor.status}
              </span>
            </div>
          ))
        )}
      </PanelBody>
    </Panel>
  );
}

function RecentAlerts({ alerts }: { alerts: readonly AlertView[] }) {
  return (
    <Panel>
      <PanelHeader>
        <PanelTitle>Recent Alerts</PanelTitle>
      </PanelHeader>
      <PanelBody className="p-0">
        {alerts.length === 0 ? (
          <div className="p-3">
            <Empty>No alerts received this session.</Empty>
          </div>
        ) : (
          <ul className="divide-y divide-border">
            {alerts.slice(0, 8).map((alert) => (
              <li key={alert.alertId} className="flex items-center gap-3 px-3 py-2 text-sm">
                <ThreatIndicator severity={severityOf(alert.severity)} label={false} />
                <span className="tnum shrink-0 text-xs text-subtle-foreground">{alert.reference}</span>
                <span className="min-w-0 flex-1 truncate">{alert.title}</span>
                <span className="shrink-0 text-2xs text-subtle-foreground">{relativeTime(alert.raisedAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </PanelBody>
    </Panel>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>;
}

function relativeTime(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : formatDistanceToNowStrict(date, { addSuffix: true });
}

const barColor: Record<Severity, string> = {
  critical: 'bg-critical',
  high: 'bg-high',
  medium: 'bg-medium',
  low: 'bg-low',
  unknown: 'bg-unknown',
};
