import { Radar } from 'lucide-react';
import type { Severity } from '@/lib/severity';
import { cn } from '@/lib/cn';
import { ConfidenceBar, Panel, PanelBody, PanelHeader, PanelTitle, ThreatIndicator } from '@/design-system';
import type { TrackView } from '@/realtime/protocol';
import { Sparkline } from './Sparkline';
import { formatAge } from './trackModel';
import { sensorContributions, TRACKS_REST_ROUTED, useTrackHistory } from './api/tracksApi';
import { useTelemetryHistory } from './useTelemetryHistory';

/**
 * Full track detail (ARCHITECTURE.md §2.4.2's TRACK DETAIL panel). Everything drawn from the live
 * stream is genuine today: identity, classification + confidence, current telemetry, and velocity/
 * altitude trends accumulated over the observed session. The server-authoritative sections — observed-
 * state history and sensor contributions (`/tracks/{id}/history`), the AI evidence-by-modality with
 * model versions, the threat-score breakdown, and the predicted trajectory/CPA — are the M11
 * integration points: the UI and the client are built, but they show a clear "awaiting M11" state
 * rather than fabricated data (the gateway does not route these sources yet — M9 finding G-4).
 */
export function TrackDetailPanel({
  track,
  severity,
}: {
  track: TrackView;
  severity: Severity;
}) {
  const telemetry = useTelemetryHistory(track);
  const history = useTrackHistory(track.trackId);
  const contributions = history.data ? sensorContributions(history.data) : [];

  return (
    <div className="flex h-full flex-col gap-3 overflow-auto p-3">
      <header className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="tnum truncate text-sm font-semibold">{track.trackId}</p>
          <p className="text-2xs text-subtle-foreground">
            {track.classLabel?.trim() || 'Unclassified'} · {track.sectorId}
          </p>
        </div>
        <ThreatIndicator severity={severity} />
      </header>

      <Panel>
        <PanelHeader>
          <PanelTitle>Telemetry</PanelTitle>
          <span className="text-2xs uppercase tracking-wide text-subtle-foreground">{track.status}</span>
        </PanelHeader>
        <PanelBody className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
          <Metric label="Confidence"><ConfidenceBar confidence={track.confidence} /></Metric>
          <Metric label="Detections"><span className="tnum">{track.detectionCount}</span></Metric>
          <Metric label="Speed">{num(track.speedMs, 'm/s')}</Metric>
          <Metric label="Heading">{num(track.headingDeg, '°')}</Metric>
          <Metric label="Altitude">{num(track.altitudeM, 'm')}</Metric>
          <Metric label="Position">
            {track.latitude != null && track.longitude != null ? (
              <span className="tnum">{track.latitude.toFixed(4)}, {track.longitude.toFixed(4)}</span>
            ) : (
              '——'
            )}
          </Metric>
          <Metric label="First seen">{formatAge(track.observedAt)} ago</Metric>
          <Metric label="Last seen">{formatAge(track.lastSeen)} ago</Metric>
        </PanelBody>
      </Panel>

      <Panel>
        <PanelHeader>
          <PanelTitle>Session trend</PanelTitle>
          <span className="text-2xs text-subtle-foreground">{telemetry.length} obs</span>
        </PanelHeader>
        <PanelBody className="space-y-3">
          <Trend label="Velocity" unit="m/s" latest={track.speedMs} values={telemetry.map((s) => s.speedMs)} />
          <Trend
            label="Altitude"
            unit="m"
            latest={track.altitudeM}
            values={telemetry.map((s) => s.altitudeM ?? 0)}
          />
          <p className="text-2xs text-subtle-foreground">
            Accumulated live while open — the observed session, not the full server history.
          </p>
        </PanelBody>
      </Panel>

      <M11Section
        title="Sensor contributions & history"
        note="Awaiting gateway route for /tracks/{id}/history (M11)"
        loading={history.isLoading}
        error={history.isError}
      >
        {contributions.length === 0 ? (
          <p className="text-xs text-muted-foreground">No sensor history for this track.</p>
        ) : (
          <ul className="space-y-1">
            {contributions.map((c) => (
              <li key={c.sensorId} className="flex items-center justify-between gap-2 text-xs">
                <span className="flex items-center gap-2">
                  <Radar className="size-3 text-muted-foreground" strokeWidth={1.75} />
                  <span className="font-medium">{c.sensorId}</span>
                </span>
                <span className="tnum text-subtle-foreground">{c.observations} obs</span>
              </li>
            ))}
          </ul>
        )}
      </M11Section>

      <M11Section
        title="AI classification & threat score"
        note="Awaiting evidence-by-modality and score breakdown from threat-assessment (M11)"
      />

      <M11Section
        title="Predicted trajectory & CPA"
        note="Awaiting predicted-path enrichment on the gateway TrackView (M11)"
      />
    </div>
  );
}

function Metric({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-2xs uppercase tracking-wide text-subtle-foreground">{label}</dt>
      <dd className="text-foreground">{children}</dd>
    </div>
  );
}

function Trend({
  label,
  unit,
  latest,
  values,
}: {
  label: string;
  unit: string;
  latest: number | null;
  values: number[];
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="w-20 shrink-0">
        <p className="text-2xs uppercase tracking-wide text-subtle-foreground">{label}</p>
        <p className="tnum text-sm">{num(latest, unit)}</p>
      </div>
      <Sparkline values={values} ariaLabel={`${label} over the observed session`} className="flex-1" />
    </div>
  );
}

/**
 * A section whose authoritative source is an M11 integration point. When the source is not yet routed
 * it shows a labelled "awaiting" state; once routed ({@link TRACKS_REST_ROUTED}) and data arrives, its
 * children render. Never fabricates data in place of the real source.
 */
function M11Section({
  title,
  note,
  loading,
  error,
  children,
}: {
  title: string;
  note: string;
  loading?: boolean;
  error?: boolean;
  children?: React.ReactNode;
}) {
  const showData = TRACKS_REST_ROUTED && children != null && !loading && !error;
  return (
    <Panel>
      <PanelHeader>
        <PanelTitle>{title}</PanelTitle>
        {!TRACKS_REST_ROUTED && (
          <span className="rounded-sm bg-elevated px-1 py-px text-[10px] font-medium text-subtle-foreground">
            M11
          </span>
        )}
      </PanelHeader>
      <PanelBody>
        {showData ? (
          children
        ) : (
          <p className={cn('text-xs', error ? 'text-warn' : 'text-subtle-foreground')}>
            {error ? 'Could not load — the source is not available yet.' : loading ? 'Loading…' : note}
          </p>
        )}
      </PanelBody>
    </Panel>
  );
}

function num(value: number | null | undefined, unit: string): string {
  if (value == null || Number.isNaN(value)) {
    return '——';
  }
  return `${Math.round(value)} ${unit}`;
}
