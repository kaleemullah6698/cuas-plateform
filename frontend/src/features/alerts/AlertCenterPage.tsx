import { Bell, ExternalLink, ShieldAlert, UserPlus } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRealtime } from '@/app/RealtimeContext';
import { useAuthStore } from '@/auth/authStore';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
  PageHeader,
  Panel,
  PanelBody,
  PanelHeader,
  PanelTitle,
  StatusDot,
  ThreatIndicator,
} from '@/design-system';
import { cn } from '@/lib/cn';
import { useAlerts, useConnection } from '@/realtime/hooks';
import { useSelectionStore } from '@/store/selectionStore';
import { formatAge } from '@/features/tracks/trackModel';
import {
  ALERT_BUCKET_LABEL,
  ALERT_BUCKETS,
  type AlertBucket,
  fromLiveAlert,
  fromRecord,
  groupAlerts,
  type QueueAlert,
} from './alertQueue';
import {
  ALERT_DISPOSITIONS,
  ALERTS_REST_ROUTED,
  type AlertDisposition,
  useAlertActions,
  useAlertQueue,
} from './api/alertsApi';

/**
 * The Alert Center (ARCHITECTURE.md §2.4.3, Screen 7): no alert missed, every alert with an owner and
 * an outcome. Three buckets — Unacknowledged / In Progress / Resolved. The live raised-alert feed
 * arrives over the WebSocket and fills the queue today; the authoritative status and the acknowledge →
 * assign → resolve workflow are the M11 integration point (alert-service is not gateway-routed yet —
 * finding G-4), gated behind {@link ALERTS_REST_ROUTED}: the actions are built and wired but disabled,
 * with a clear reason, until the route lands. Opening the source track works today.
 */
export function AlertCenterPage() {
  const realtime = useRealtime();
  const liveAlerts = useAlerts(realtime.alerts);
  const connection = useConnection(realtime.connection);
  const queue = useAlertQueue();
  const navigate = useNavigate();
  const select = useSelectionStore((s) => s.select);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const alerts: QueueAlert[] = useMemo(() => {
    if (ALERTS_REST_ROUTED && queue.data) {
      return queue.data.map(fromRecord);
    }
    return liveAlerts.map(fromLiveAlert);
  }, [queue.data, liveAlerts]);

  const groups = useMemo(() => groupAlerts(alerts), [alerts]);
  const flat = useMemo(() => ALERT_BUCKETS.flatMap((b) => groups[b]), [groups]);
  const selected = selectedId ? alerts.find((a) => a.alertId === selectedId) ?? null : null;
  const live = connection.status === 'CONNECTED';

  const openTrack = useCallback(
    (alert: QueueAlert) => {
      if (alert.trackId) {
        select(alert.trackId);
        navigate('/tracks');
      }
    },
    [select, navigate],
  );

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      const key = event.key.toLowerCase();
      if (key === 'j' || key === 'k') {
        event.preventDefault();
        if (flat.length === 0) return;
        const idx = flat.findIndex((a) => a.alertId === selectedId);
        const next = idx < 0 ? 0 : Math.min(flat.length - 1, Math.max(0, idx + (key === 'j' ? 1 : -1)));
        setSelectedId(flat[next]?.alertId ?? null);
      } else if (key === 'o' && selected) {
        openTrack(selected);
      } else if (key === 'escape') {
        setSelectedId(null);
      }
    },
    [flat, selectedId, selected, openTrack],
  );

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={Bell} title="Alert Center">
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <StatusDot status={live ? 'ok' : 'down'} pulse={live} />
          {live ? 'Live' : 'Offline'}
          <span className="tnum ml-2 text-subtle-foreground">{alerts.length} alerts</span>
        </span>
      </PageHeader>

      {!ALERTS_REST_ROUTED && (
        <p className="border-b border-border bg-elevated/40 px-4 py-1.5 text-2xs text-subtle-foreground">
          Showing the live raised-alert feed. Authoritative status and the acknowledge → assign →
          resolve workflow arrive when the gateway routes alert-service (M11).
        </p>
      )}

      <div
        role="grid"
        aria-label="Alert queue"
        tabIndex={0}
        onKeyDown={onKeyDown}
        className="grid flex-1 grid-cols-1 gap-3 overflow-auto p-3 outline-none focus-visible:ring-2 focus-visible:ring-ring lg:grid-cols-3"
      >
        {ALERT_BUCKETS.map((bucket) => (
          <BucketColumn
            key={bucket}
            bucket={bucket}
            alerts={groups[bucket]}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
        ))}
      </div>

      {selected && (
        <AlertActionBar alert={selected} onOpenTrack={() => openTrack(selected)} onEscalate={() => navigate('/incidents')} />
      )}
    </div>
  );
}

function BucketColumn({
  bucket,
  alerts,
  selectedId,
  onSelect,
}: {
  bucket: AlertBucket;
  alerts: QueueAlert[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  return (
    <Panel className="flex min-h-0 flex-col">
      <PanelHeader>
        <PanelTitle>{ALERT_BUCKET_LABEL[bucket]}</PanelTitle>
        <span className="tnum text-2xs text-subtle-foreground">{alerts.length}</span>
      </PanelHeader>
      <PanelBody className="min-h-0 flex-1 space-y-1 overflow-auto p-1.5">
        {alerts.length === 0 ? (
          <p className="p-2 text-xs text-subtle-foreground">
            {bucket === 'unacknowledged' ? 'No open alerts.' : '—'}
          </p>
        ) : (
          alerts.map((alert) => (
            <button
              key={alert.alertId}
              type="button"
              onClick={() => onSelect(alert.alertId)}
              aria-pressed={alert.alertId === selectedId}
              className={cn(
                'flex w-full items-center gap-2 rounded-sm border border-transparent px-2 py-1.5 text-left text-xs hover:bg-elevated/60',
                alert.alertId === selectedId && 'border-border-strong bg-elevated',
              )}
            >
              <ThreatIndicator severity={alert.severity} label={false} />
              <span className="tnum shrink-0 text-subtle-foreground">{alert.reference}</span>
              <span className="min-w-0 flex-1 truncate">{alert.title}</span>
              <span className="shrink-0 text-2xs text-subtle-foreground">{formatAge(alert.raisedAt)}</span>
            </button>
          ))
        )}
      </PanelBody>
    </Panel>
  );
}

function AlertActionBar({
  alert,
  onOpenTrack,
  onEscalate,
}: {
  alert: QueueAlert;
  onOpenTrack: () => void;
  onEscalate: () => void;
}) {
  const subject = useAuthStore((s) => s.subject);
  const { acknowledge, assign, resolve } = useAlertActions();
  // The lifecycle mutations need alert-service routed through the gateway (M11, finding G-4). Until then
  // the actions are disabled; the banner above says why. Opening the source track works today.
  const gated = !ALERTS_REST_ROUTED;
  const done = alert.status === 'RESOLVED' || alert.status === 'SUPPRESSED';

  return (
    <div className="flex items-center gap-2 border-t border-border bg-card px-4 py-2">
      <ThreatIndicator severity={alert.severity} />
      <span className="tnum text-xs text-subtle-foreground">{alert.reference}</span>
      <span className="min-w-0 flex-1 truncate text-xs">{alert.title}</span>

      <Button variant="ghost" size="sm" onClick={onOpenTrack} disabled={!alert.trackId}>
        <ExternalLink className="size-3.5" /> Open track
      </Button>

      <Button
        variant="secondary"
        size="sm"
        disabled={gated || done || acknowledge.isPending}
        onClick={() => acknowledge.mutate({ alertId: alert.alertId })}
      >
        Acknowledge
      </Button>

      <Button
        variant="ghost"
        size="sm"
        disabled={gated || done || !subject || assign.isPending}
        onClick={() => subject && assign.mutate({ alertId: alert.alertId, assigneeId: subject })}
      >
        <UserPlus className="size-3.5" /> Assign to me
      </Button>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="primary" size="sm" disabled={gated || done || resolve.isPending}>
            Resolve ▾
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuLabel>Disposition</DropdownMenuLabel>
          {ALERT_DISPOSITIONS.map((disposition: AlertDisposition) => (
            <DropdownMenuItem
              key={disposition}
              onSelect={() => resolve.mutate({ alertId: alert.alertId, disposition })}
            >
              {disposition.replace('_', ' ').toLowerCase()}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>

      <Button variant="outline" size="sm" disabled={gated} onClick={onEscalate}>
        <ShieldAlert className="size-3.5" /> Escalate
      </Button>
    </div>
  );
}
