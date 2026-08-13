import { History, Radio } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRealtime } from '@/app/RealtimeContext';
import { Badge, PageHeader, Spinner, StatusDot } from '@/design-system';
import { cn } from '@/lib/cn';
import { SEVERITY_SHORT, type Severity } from '@/lib/severity';
import { useAlerts } from '@/realtime/hooks';
import { useSelectionStore } from '@/store/selectionStore';
import { type AlertRecord, useAlertQueue } from '@/features/alerts/api/alertsApi';

/**
 * Threat Timeline (ARCHITECTURE.md §2 Screen 5).
 *
 * Screen 5's primary goal is to "understand how the airspace picture evolved over a window, and
 * reconstruct sequence for after-action review". That is what this screen does, from real data:
 * alert-service's raised alerts in chronological order, each carrying its severity, composite threat
 * score, §6.3.4 recommendation and source track.
 *
 * <p><strong>What is deliberately absent, and why.</strong> Screen 5 also specifies time scrubbing
 * and 1×/2×/8× playback of the picture at an arbitrary instant. **No backend contract supports it.**
 * `/api/v1/alerts` and `/api/v1/tracks` order by recency and filter by status, severity and sector;
 * neither accepts a `from`/`to` window, and there is no endpoint that reconstructs the airspace at a
 * past instant. Building a scrubber over data that cannot answer "what did it look like at T" would
 * be a fabricated control. The missing contract is named in the footer rather than faked.
 *
 * <p>Live-follow (`L`) merges the WebSocket raised-alert feed into the head of the timeline, so the
 * screen keeps up with the airspace while it is open. Turning it off freezes the view for reading —
 * the honest version of "historical mode", and the banner says which one you are in.
 */
export function ThreatTimelinePage() {
  const realtime = useRealtime();
  const liveAlerts = useAlerts(realtime.alerts);
  const query = useAlertQueue();
  const navigate = useNavigate();
  const select = useSelectionStore((s) => s.select);

  const [follow, setFollow] = useState(true);
  const [minSeverity, setMinSeverity] = useState<Severity>('unknown');
  const [cursor, setCursor] = useState(0);

  // The recorded timeline is the REST history; the live feed adds what has arrived since it loaded.
  // Merged by alertId so an alert that appears in both is one entry, not two.
  const events = useMemo(() => {
    const byId = new Map<string, TimelineEvent>();
    for (const record of query.data ?? []) byId.set(record.alertId, fromRecord(record));
    if (follow) {
      for (const live of liveAlerts) {
        if (!byId.has(live.alertId)) {
          byId.set(live.alertId, {
            alertId: live.alertId,
            reference: live.reference,
            severity: live.severity,
            trackId: live.trackId,
            title: live.title,
            raisedAt: live.raisedAt,
            threatScore: null,
            recommendation: null,
            live: true,
          });
        }
      }
    }
    return [...byId.values()]
      .filter((e) => rank(e.severity) >= rank(severityWire(minSeverity)))
      .sort((a, b) => (Date.parse(b.raisedAt ?? '') || 0) - (Date.parse(a.raisedAt ?? '') || 0));
  }, [query.data, liveAlerts, follow, minSeverity]);

  // §2 Screen 5 keyboard: ←/→ step one event, L toggles live-follow.
  const onKey = useCallback(
    (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
      if (e.key === 'ArrowDown' || e.key === 'ArrowRight') {
        e.preventDefault();
        setCursor((c) => Math.min(c + 1, Math.max(0, events.length - 1)));
      } else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') {
        e.preventDefault();
        setCursor((c) => Math.max(0, c - 1));
      } else if (e.key.toLowerCase() === 'l') {
        setFollow((f) => !f);
      }
    },
    [events.length],
  );

  useEffect(() => {
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onKey]);

  const open = (event: TimelineEvent) => {
    select(event.trackId);
    navigate('/tracks');
  };

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={History} title="Threat Timeline">
        <button
          type="button"
          onClick={() => setFollow((f) => !f)}
          aria-pressed={follow}
          className="flex items-center gap-1.5 rounded-sm border border-border px-2 py-1 text-xs text-muted-foreground hover:bg-surface-2"
        >
          <StatusDot status={follow ? 'ok' : 'warn'} pulse={follow} />
          {follow ? 'Live-follow on' : 'Frozen for review'}
          <kbd className="ml-1 rounded-sm bg-elevated px-1 font-mono text-[10px]">L</kbd>
        </button>
      </PageHeader>

      {!follow && (
        <div
          role="status"
          className="flex items-center gap-2 border-b border-warn/40 bg-warn/10 px-4 py-2 text-xs font-semibold text-warn"
        >
          <Radio className="size-3.5" />
          HISTORICAL — NOT LIVE. New alerts are still arriving and will appear when live-follow resumes.
        </div>
      )}

      <div className="flex items-center gap-3 border-b border-border px-4 py-2 text-xs">
        <label className="flex items-center gap-1.5 text-muted-foreground">
          Threat ≥
          <select
            value={minSeverity}
            onChange={(e) => setMinSeverity(e.target.value as Severity)}
            aria-label="Minimum threat level"
            className="rounded-sm border border-input bg-surface px-1.5 py-1 text-xs text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {(['unknown', 'low', 'medium', 'high', 'critical'] as const).map((s) => (
              <option key={s} value={s}>{SEVERITY_SHORT[s]}</option>
            ))}
          </select>
        </label>
        <span className="tnum ml-auto text-subtle-foreground">{events.length} events</span>
      </div>

      <div className="flex-1 overflow-auto">
        {query.isLoading ? (
          <div className="grid h-full place-items-center"><Spinner /></div>
        ) : events.length === 0 ? (
          <p className="p-8 text-center text-sm text-muted-foreground">
            No threat events in the recorded history. Events appear here as alert-service raises them.
          </p>
        ) : (
          <ol className="divide-y divide-border/50">
            {events.map((event, index) => (
              <li key={event.alertId}>
                <button
                  type="button"
                  onClick={() => { setCursor(index); open(event); }}
                  className={cn(
                    'flex w-full items-center gap-3 px-4 py-2 text-left text-xs hover:bg-surface-2',
                    index === cursor && 'bg-surface-2 ring-1 ring-inset ring-ring',
                  )}
                >
                  <time className="tnum w-[168px] shrink-0 text-muted-foreground">
                    {event.raisedAt ? event.raisedAt.replace('T', ' ').replace(/\.\d+Z?$/, 'Z') : '——'}
                  </time>
                  <Badge variant={badgeVariant(event.severity)}>{shortSeverity(event.severity)}</Badge>
                  <span className="w-24 shrink-0 font-mono">{event.reference}</span>
                  <span className="min-w-0 flex-1 truncate text-foreground">{event.title}</span>
                  {event.threatScore !== null && (
                    <span className="tnum w-12 shrink-0 text-right text-muted-foreground">
                      {event.threatScore.toFixed(3)}
                    </span>
                  )}
                  {event.recommendation && (
                    <span className="w-44 shrink-0 truncate text-subtle-foreground">
                      {readable(event.recommendation)}
                    </span>
                  )}
                  {event.live && <Badge variant="ok">LIVE</Badge>}
                </button>
              </li>
            ))}
          </ol>
        )}
      </div>

      <footer className="border-t border-border px-4 py-2 text-[11px] text-subtle-foreground">
        <kbd className="rounded-sm bg-elevated px-1 font-mono">←</kbd>/
        <kbd className="rounded-sm bg-elevated px-1 font-mono">→</kbd> step ·
        <kbd className="ml-1 rounded-sm bg-elevated px-1 font-mono">L</kbd> live-follow · click an
        event to open its track. Time scrubbing and 1×/2×/8× replay (§2 Screen 5) are not shown: no
        endpoint accepts a time window or reconstructs the airspace at a past instant, and a scrubber
        over data that cannot answer it would be a fabricated control.
      </footer>
    </div>
  );
}

interface TimelineEvent {
  alertId: string;
  reference: string;
  severity: string;
  trackId: string;
  title: string;
  raisedAt: string | null;
  threatScore: number | null;
  recommendation: string | null;
  live: boolean;
}

function fromRecord(record: AlertRecord): TimelineEvent {
  return {
    alertId: record.alertId,
    reference: record.reference,
    severity: record.severity,
    trackId: record.trackId,
    title: record.title,
    raisedAt: record.raisedAt,
    threatScore: record.threatScore,
    recommendation: record.recommendation,
    live: false,
  };
}

/** The wire carries THREAT_LEVEL_* or a bare name; reduce without inventing a level. */
function shortSeverity(severity: string): string {
  const s = severity.toUpperCase();
  if (s.includes('CRITICAL')) return 'CRIT';
  if (s.includes('HIGH')) return 'HIGH';
  if (s.includes('MEDIUM')) return 'MED';
  if (s.includes('LOW')) return 'LOW';
  return 'UNK';
}

function rank(severity: string): number {
  switch (shortSeverity(severity)) {
    case 'CRIT': return 4;
    case 'HIGH': return 3;
    case 'MED': return 2;
    case 'LOW': return 1;
    default: return 0;
  }
}

function severityWire(severity: Severity): string {
  return severity.toUpperCase();
}

function badgeVariant(severity: string): 'critical' | 'high' | 'medium' | 'low' | 'unknown' {
  switch (shortSeverity(severity)) {
    case 'CRIT': return 'critical';
    case 'HIGH': return 'high';
    case 'MED': return 'medium';
    case 'LOW': return 'low';
    default: return 'unknown';
  }
}

/** RECOMMEND_OPERATOR_REVIEW -> "Operator review". The vocabulary is closed (§6.3.4). */
function readable(recommendation: string): string {
  const bare = recommendation.replace(/^RECOMMEND_/, '').replace(/_/g, ' ').toLowerCase();
  return bare.charAt(0).toUpperCase() + bare.slice(1);
}
