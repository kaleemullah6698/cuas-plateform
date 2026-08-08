import { Layers, Map as MapIcon, X } from 'lucide-react';
import { useMemo } from 'react';
import { useRealtime } from '@/app/RealtimeContext';
import { ConfidenceBar, PageHeader, StatusDot, ThreatIndicator } from '@/design-system';
import { cn } from '@/lib/cn';
import { SEVERITY_ORDER, type Severity, SEVERITY_SHAPE, SEVERITY_SHORT } from '@/lib/severity';
import { useAlerts, useConnection, useTracks } from '@/realtime/hooks';
import { useSelectionStore } from '@/store/selectionStore';
import { severityByTrack } from './geojson';
import { MAP_LAYERS, useMapLayers } from './layers';
import { MapView } from './MapView';

/**
 * The Live Map screen (ARCHITECTURE.md §2.4.2). The map is the surface; everything else floats over it
 * as command-centre glass panels rather than sitting in a grid of cards — an operator watches the
 * airspace, not a dashboard. The legend, the layer control, and the selected-track readout overlay the
 * map without stealing its space. Only genuinely-available data is drawn (M10.D, Option A); the layer
 * control lists the M11 layers as present-but-disabled so their arrival is visible, not a surprise.
 */
export function MapPage() {
  const realtime = useRealtime();
  const connection = useConnection(realtime.connection);
  const tracks = useTracks(realtime.tracks);
  const alerts = useAlerts(realtime.alerts);
  const selectedTrackId = useSelectionStore((s) => s.selectedTrackId);

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={MapIcon} title="Live Map">
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <StatusDot
            status={connection.status === 'CONNECTED' ? 'ok' : 'down'}
            pulse={connection.status === 'CONNECTED'}
          />
          {connection.status === 'CONNECTED' ? 'Live' : 'Offline'}
          <span className="tnum ml-2 text-subtle-foreground">{tracks.length} tracks</span>
        </span>
      </PageHeader>

      <div className="relative flex-1 overflow-hidden">
        <MapView />

        <div className="pointer-events-none absolute inset-0 p-3">
          <div className="flex h-full items-start justify-between gap-3">
            <Legend />
            <LayerControl />
          </div>
          <SelectedTrackCard trackId={selectedTrackId} tracks={tracks} alerts={alerts} />
        </div>
      </div>
    </div>
  );
}

const panel =
  'pointer-events-auto rounded-md border border-border bg-popover/90 backdrop-blur-sm shadow-lg';

function Legend() {
  return (
    <div className={cn(panel, 'p-2.5')}>
      <p className="mb-1.5 text-2xs font-semibold uppercase tracking-wide text-subtle-foreground">
        Threat
      </p>
      <ul className="space-y-1">
        {SEVERITY_ORDER.map((severity) => (
          <li key={severity} className="flex items-center gap-2 text-xs">
            <span className={cn('w-3 text-center', legendColor[severity])} aria-hidden="true">
              {SEVERITY_SHAPE[severity]}
            </span>
            <span className="text-muted-foreground">{SEVERITY_SHORT[severity]}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function LayerControl() {
  const visible = useMapLayers((s) => s.visible);
  const toggle = useMapLayers((s) => s.toggle);
  return (
    <div className={cn(panel, 'w-52 p-2.5')}>
      <p className="mb-1.5 flex items-center gap-1.5 text-2xs font-semibold uppercase tracking-wide text-subtle-foreground">
        <Layers className="size-3" strokeWidth={1.75} /> Layers
      </p>
      <ul className="space-y-0.5">
        {MAP_LAYERS.map((layer) => (
          <li key={layer.id}>
            <label
              className={cn(
                'flex items-center gap-2 rounded-sm px-1 py-1 text-xs',
                layer.available ? 'cursor-pointer hover:bg-elevated' : 'cursor-not-allowed opacity-55',
              )}
            >
              <input
                type="checkbox"
                className="size-3.5 accent-primary"
                checked={visible[layer.id]}
                disabled={!layer.available}
                onChange={() => toggle(layer.id)}
              />
              <span className="flex-1">{layer.label}</span>
              {!layer.available && (
                <span className="rounded-sm bg-elevated px-1 py-px text-[10px] font-medium text-subtle-foreground">
                  M11
                </span>
              )}
            </label>
            {!layer.available && layer.note && (
              <p className="pl-6 text-[10px] leading-tight text-subtle-foreground">{layer.note}</p>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

function SelectedTrackCard({
  trackId,
  tracks,
  alerts,
}: {
  trackId: string | null;
  tracks: ReturnType<typeof useTracks>;
  alerts: ReturnType<typeof useAlerts>;
}) {
  const select = useSelectionStore((s) => s.select);
  const severity = useMemo(() => severityByTrack(alerts), [alerts]);
  const track = trackId ? tracks.find((t) => t.trackId === trackId) : undefined;
  if (!trackId) {
    return null;
  }
  const level = severity.get(trackId) ?? 'unknown';
  return (
    <div className={cn(panel, 'absolute bottom-3 left-3 w-72 p-3')}>
      <div className="mb-2 flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="tnum truncate text-sm font-semibold">{trackId}</p>
          <p className="text-2xs text-subtle-foreground">
            {track?.classLabel?.trim() || 'Unclassified'}
          </p>
        </div>
        <div className="flex items-center gap-1.5">
          <ThreatIndicator severity={level} />
          <button
            type="button"
            onClick={() => select(null)}
            aria-label="Clear selection"
            className="rounded-sm p-0.5 text-muted-foreground hover:bg-elevated hover:text-foreground"
          >
            <X className="size-3.5" />
          </button>
        </div>
      </div>
      {track ? (
        <dl className="grid grid-cols-2 gap-x-3 gap-y-1.5 text-xs">
          <Field label="Confidence">
            <ConfidenceBar confidence={track.confidence} />
          </Field>
          <Field label="Status">{track.status || '—'}</Field>
          <Field label="Speed">{fmt(track.speedMs, 'm/s')}</Field>
          <Field label="Heading">{fmt(track.headingDeg, '°')}</Field>
          <Field label="Altitude">{fmt(track.altitudeM, 'm')}</Field>
          <Field label="Detections">
            <span className="tnum">{track.detectionCount}</span>
          </Field>
        </dl>
      ) : (
        <p className="text-xs text-muted-foreground">Track no longer in view.</p>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-2xs uppercase tracking-wide text-subtle-foreground">{label}</dt>
      <dd className="text-foreground">{children}</dd>
    </div>
  );
}

function fmt(value: number | null | undefined, unit: string): string {
  if (value == null || Number.isNaN(value)) {
    return '——';
  }
  return `${Math.round(value)} ${unit}`;
}

const legendColor: Record<Severity, string> = {
  critical: 'text-critical',
  high: 'text-high',
  medium: 'text-medium',
  low: 'text-low',
  unknown: 'text-unknown',
};
