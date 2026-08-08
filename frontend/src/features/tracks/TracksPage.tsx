import { Radar, Search } from 'lucide-react';
import { useMemo } from 'react';
import { useRealtime } from '@/app/RealtimeContext';
import { Input, PageHeader, StatusDot } from '@/design-system';
import { SEVERITY_ORDER, SEVERITY_SHORT, type Severity } from '@/lib/severity';
import { severityByTrack } from '@/lib/trackSeverity';
import { useAlerts, useConnection, useTracks } from '@/realtime/hooks';
import { useSelectionStore } from '@/store/selectionStore';
import { TrackDetailPanel } from './TrackDetailPanel';
import { TrackTable } from './TrackTable';
import { useTrackFilters } from './trackFilters';
import { filterTracks, severityOfTrack, severityRank, sortTracks } from './trackModel';

/**
 * The Real-Time Track Console (ARCHITECTURE.md §2.4.2, Screen 4). The live track list worked as a
 * triage queue on the left; the selected track's full detail on the right. Everything is driven by the
 * real-time stores at the §2.5.1 track cadence — no polling — and threat, derived from the live alert
 * stream, is the primary sort key so the most dangerous track is always at the top.
 */
export function TracksPage() {
  const realtime = useRealtime();
  const tracks = useTracks(realtime.tracks);
  const alerts = useAlerts(realtime.alerts);
  const connection = useConnection(realtime.connection);
  const selectedTrackId = useSelectionStore((s) => s.selectedTrackId);
  const select = useSelectionStore((s) => s.select);
  const { minSeverity, query, setMinSeverity, setQuery } = useTrackFilters();

  const severity = useMemo(() => severityByTrack(alerts), [alerts]);
  const visible = useMemo(() => {
    const filtered = filterTracks(tracks, severity, severityRank(minSeverity), query);
    return sortTracks(filtered, severity);
  }, [tracks, severity, minSeverity, query]);

  const selectedTrack = selectedTrackId ? tracks.find((t) => t.trackId === selectedTrackId) : undefined;
  const live = connection.status === 'CONNECTED';

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={Radar} title="Track Console">
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <StatusDot status={live ? 'ok' : 'down'} pulse={live} />
          {live ? 'Live' : 'Offline'}
          <span className="tnum ml-2 text-subtle-foreground">
            {visible.length}/{tracks.length} tracks
          </span>
        </span>
      </PageHeader>

      <div className="flex items-center gap-2 border-b border-border px-4 py-2">
        <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
          Threat ≥
          <select
            value={minSeverity}
            onChange={(e) => setMinSeverity(e.target.value as Severity)}
            className="rounded-sm border border-input bg-surface px-1.5 py-1 text-xs text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="Minimum threat level"
          >
            {[...SEVERITY_ORDER].reverse().map((s) => (
              <option key={s} value={s}>
                {SEVERITY_SHORT[s]}
              </option>
            ))}
          </select>
        </label>
        <div className="relative ml-auto w-64">
          <Search className="pointer-events-none absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-subtle-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search track or class…"
            aria-label="Search tracks"
            className="h-8 pl-7 text-xs"
          />
        </div>
      </div>

      <div className="grid flex-1 grid-cols-1 gap-3 overflow-hidden p-3 lg:grid-cols-[1fr_360px]">
        <TrackTable
          tracks={visible}
          severity={severity}
          selectedTrackId={selectedTrackId}
          onSelect={select}
        />
        <aside className="hidden overflow-hidden rounded-md border border-border bg-card lg:block">
          {selectedTrack ? (
            <TrackDetailPanel
              track={selectedTrack}
              severity={severityOfTrack(selectedTrack.trackId, severity)}
            />
          ) : (
            <div className="grid h-full place-items-center p-6 text-center text-sm text-muted-foreground">
              Select a track to see its detail.
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
