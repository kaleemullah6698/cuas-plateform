import { useCallback, useEffect, useRef, useState } from 'react';
import type { Severity } from '@/lib/severity';
import { cn } from '@/lib/cn';
import { ConfidenceBar, ThreatIndicator } from '@/design-system';
import type { TrackView } from '@/realtime/protocol';
import { formatAge, severityOfTrack } from './trackModel';

/**
 * The track triage queue (ARCHITECTURE.md §2.4.2). A dense, threat-sorted table bound to the live
 * stream; rows are selected by click or keyboard. Per §2.4.2 the keyboard is first-class for sustained
 * triage: {@code J}/{@code K} move down/up the queue (vim muscle-memory), {@code Enter} confirms the
 * selection, {@code Escape} clears it. Selection is shared state, so choosing a row here also drives the
 * map and the detail panel. Classification and escalation ([H]/[B]/[E]) are the alert/incident workflow
 * and land in M10.F.
 */
export function TrackTable({
  tracks,
  severity,
  selectedTrackId,
  onSelect,
}: {
  tracks: readonly TrackView[];
  severity: Map<string, Severity>;
  selectedTrackId: string | null;
  onSelect: (trackId: string | null) => void;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [now, setNow] = useState(() => Date.now());

  // Keep ages fresh at the §2.5.1 1 Hz timeline cadence even when the stream is momentarily quiet.
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);

  const move = useCallback(
    (delta: number) => {
      if (tracks.length === 0) {
        return;
      }
      const index = tracks.findIndex((t) => t.trackId === selectedTrackId);
      const next = index < 0 ? (delta > 0 ? 0 : tracks.length - 1) : clamp(index + delta, tracks.length);
      const target = tracks[next];
      if (target) {
        onSelect(target.trackId);
      }
    },
    [tracks, selectedTrackId, onSelect],
  );

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      const key = event.key.toLowerCase();
      if (key === 'j' || key === 'arrowdown') {
        event.preventDefault();
        move(1);
      } else if (key === 'k' || key === 'arrowup') {
        event.preventDefault();
        move(-1);
      } else if (key === 'escape') {
        onSelect(null);
      }
    },
    [move, onSelect],
  );

  return (
    <div
      ref={containerRef}
      role="grid"
      aria-label="Live tracks"
      tabIndex={0}
      onKeyDown={onKeyDown}
      className="h-full overflow-auto rounded-md border border-border outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <table className="w-full border-collapse text-xs">
        <thead className="sticky top-0 z-10 bg-card">
          <tr className="border-b border-border text-2xs uppercase tracking-wide text-subtle-foreground">
            {['Threat', 'Track', 'Class', 'Confidence', 'Speed', 'Hdg', 'Alt', 'Status', 'Age'].map((h) => (
              <th key={h} className="px-2 py-1.5 text-left font-medium">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {tracks.length === 0 ? (
            <tr>
              <td colSpan={9} className="px-2 py-6 text-center text-sm text-muted-foreground">
                No tracks match the current filter.
              </td>
            </tr>
          ) : (
            tracks.map((track) => {
              const level = severityOfTrack(track.trackId, severity);
              const selected = track.trackId === selectedTrackId;
              return (
                <tr
                  key={track.trackId}
                  role="row"
                  aria-selected={selected}
                  onClick={() => onSelect(track.trackId)}
                  className={cn(
                    'cursor-pointer border-b border-border/60 hover:bg-elevated/60',
                    selected && 'bg-elevated',
                  )}
                >
                  <td className="px-2 py-1.5"><ThreatIndicator severity={level} /></td>
                  <td className="tnum px-2 py-1.5 font-medium">{track.trackId}</td>
                  <td className="px-2 py-1.5 text-muted-foreground">{track.classLabel?.trim() || '——'}</td>
                  <td className="px-2 py-1.5"><ConfidenceBar confidence={track.confidence} /></td>
                  <td className="tnum px-2 py-1.5">{num(track.speedMs, 'm/s')}</td>
                  <td className="tnum px-2 py-1.5">{num(track.headingDeg, '°')}</td>
                  <td className="tnum px-2 py-1.5">{num(track.altitudeM, 'm')}</td>
                  <td className="px-2 py-1.5 text-2xs uppercase tracking-wide text-muted-foreground">{track.status}</td>
                  <td className="tnum px-2 py-1.5 text-subtle-foreground">{formatAge(track.lastSeen, now)}</td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}

function clamp(index: number, length: number): number {
  if (index < 0) {
    return 0;
  }
  if (index >= length) {
    return length - 1;
  }
  return index;
}

function num(value: number | null | undefined, unit: string): string {
  if (value == null || Number.isNaN(value)) {
    return '——';
  }
  return `${Math.round(value)} ${unit}`;
}
