import { Radio } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useRealtime } from '@/app/RealtimeContext';
import { Input, PageHeader, StatusDot } from '@/design-system';
import { cn } from '@/lib/cn';
import { useConnection, useSensors } from '@/realtime/hooks';
import type { SensorView } from '@/realtime/protocol';

/**
 * Sensor Health Monitoring (ARCHITECTURE.md §2 Screen 6, §4.1.4).
 *
 * Live today, and driven by exactly the same `sensors` WebSocket channel the dashboard's coverage
 * tile already consumes — no REST call and no polling. sensor-ingestion derives the status from how
 * recently it last saw each sensor (`health.online-within`, 30 s; `degraded-within`, 2 m) and emits
 * on `sensor.health.v1`; the gateway fans it out unchanged.
 *
 * <p>Coverage is the operational question this screen answers: an operator needs to know that a gap
 * in the airspace picture is a *sensor* problem rather than an absence of targets. Offline sensors
 * therefore sort first — the failures are what warrant attention, not the healthy majority.
 */
export function SensorHealthPage() {
  const realtime = useRealtime();
  const sensors = useSensors(realtime.sensors);
  const connection = useConnection(realtime.connection);
  const [query, setQuery] = useState('');

  const rows = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const filtered = needle
      ? sensors.filter((s) => s.sensorId.toLowerCase().includes(needle))
      : sensors;
    // Impaired first, then by id, so the list is stable while the stream updates underneath it.
    return [...filtered].sort((a, b) => {
      const rank = severityRank(b.status) - severityRank(a.status);
      return rank !== 0 ? rank : a.sensorId.localeCompare(b.sensorId);
    });
  }, [sensors, query]);

  const counts = useMemo(() => tally(sensors), [sensors]);
  const live = connection.status === 'CONNECTED';

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={Radio} title="Sensor Health">
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <StatusDot status={live ? 'ok' : 'down'} pulse={live} />
          {live ? 'Live' : 'Offline'}
          <span className="tnum ml-2 text-subtle-foreground">
            {counts.online}/{counts.total} online
          </span>
        </span>
      </PageHeader>

      <div className="flex items-center gap-4 border-b border-border px-4 py-2">
        <dl className="flex items-center gap-4 text-xs">
          <Tally label="Online" value={counts.online} tone="ok" />
          <Tally label="Degraded" value={counts.degraded} tone="warn" />
          <Tally label="Offline" value={counts.offline} tone="down" />
        </dl>
        <div className="ml-auto w-64">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search sensor…"
            aria-label="Search sensors"
            className="h-8 text-xs"
          />
        </div>
      </div>

      <div className="flex-1 overflow-auto">
        {rows.length === 0 ? (
          <p className="p-8 text-center text-sm text-muted-foreground">
            {sensors.length === 0
              ? 'No sensors have reported yet. Sensor state arrives on the live feed as each sensor is heard from.'
              : 'No sensor matches the current filter.'}
          </p>
        ) : (
          <table className="w-full text-xs">
            <thead className="sticky top-0 bg-surface">
              <tr className="border-b border-border text-left text-subtle-foreground">
                <th scope="col" className="px-4 py-2 font-medium">Status</th>
                <th scope="col" className="px-4 py-2 font-medium">Sensor</th>
                <th scope="col" className="px-4 py-2 font-medium">Last seen (UTC)</th>
                <th scope="col" className="px-4 py-2 font-medium">Age</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((sensor) => (
                <tr key={sensor.sensorId} className="border-b border-border/50 hover:bg-surface-2">
                  <td className="px-4 py-1.5">
                    <span className="flex items-center gap-1.5">
                      <StatusDot status={dotStatus(sensor.status)} />
                      <span className={cn('font-medium', toneClass(sensor.status))}>
                        {normalise(sensor.status)}
                      </span>
                    </span>
                  </td>
                  <td className="px-4 py-1.5 font-mono">{sensor.sensorId}</td>
                  <td className="tnum px-4 py-1.5 text-muted-foreground">
                    {sensor.lastSeen ? sensor.lastSeen.replace('T', ' ').replace(/\.\d+Z?$/, 'Z') : '——'}
                  </td>
                  <td className="tnum px-4 py-1.5 text-muted-foreground">{age(sensor.lastSeen)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function Tally({ label, value, tone }: { label: string; value: number; tone: 'ok' | 'warn' | 'down' }) {
  return (
    <div className="flex items-center gap-1.5">
      <StatusDot status={tone} />
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="tnum font-semibold text-foreground">{value}</dd>
    </div>
  );
}

/** The wire carries the enum name; render it as a word without inventing a status we were not sent. */
function normalise(status: string): string {
  const s = status.toUpperCase();
  if (s.includes('ONLINE')) return 'ONLINE';
  if (s.includes('DEGRADED')) return 'DEGRADED';
  if (s.includes('OFFLINE')) return 'OFFLINE';
  return status;
}

function severityRank(status: string): number {
  const s = normalise(status);
  return s === 'OFFLINE' ? 2 : s === 'DEGRADED' ? 1 : 0;
}

function dotStatus(status: string): 'ok' | 'warn' | 'down' {
  const s = normalise(status);
  return s === 'ONLINE' ? 'ok' : s === 'DEGRADED' ? 'warn' : 'down';
}

function toneClass(status: string): string {
  const s = normalise(status);
  return s === 'ONLINE' ? 'text-ok' : s === 'DEGRADED' ? 'text-warn' : 'text-down';
}

/** Elapsed time since the sensor was last heard from — the number the status is derived from. */
function age(lastSeen: string | null): string {
  if (!lastSeen) return '——';
  const then = Date.parse(lastSeen);
  if (Number.isNaN(then)) return '——';
  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}

function tally(sensors: readonly SensorView[]) {
  let online = 0;
  let degraded = 0;
  let offline = 0;
  for (const s of sensors) {
    const n = normalise(s.status);
    if (n === 'ONLINE') online += 1;
    else if (n === 'DEGRADED') degraded += 1;
    else offline += 1;
  }
  return { online, degraded, offline, total: sensors.length };
}
