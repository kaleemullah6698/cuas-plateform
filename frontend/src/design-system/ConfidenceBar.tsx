import { cn } from '@/lib/cn';

/**
 * Confidence, one consistent representation everywhere it appears (§2.5.6). The fill is **neutral** —
 * confidence and threat are orthogonal and must never be conflated, so the bar never takes a threat
 * hue. Absent confidence renders `——`, never `0%` (a missing measurement and a zero are different
 * facts); below 0.50 the number is de-emphasised with a LOW caption.
 */
export function ConfidenceBar({
  confidence,
  className,
}: {
  confidence: number | null | undefined;
  className?: string;
}) {
  if (confidence == null) {
    return <span className={cn('tnum text-muted-foreground', className)}>——</span>;
  }
  const pct = Math.round(confidence * 100);
  const low = confidence < 0.5;
  return (
    <span className={cn('inline-flex items-center gap-1.5', className)}>
      <span className="h-1.5 w-16 overflow-hidden rounded-full bg-elevated">
        <span
          className="block h-full rounded-full bg-muted-foreground"
          style={{ width: `${pct}%` }}
        />
      </span>
      <span className={cn('tnum text-xs', low ? 'text-unknown' : 'text-muted-foreground')}>{pct}%</span>
      {low && <span className="text-2xs uppercase tracking-wide text-unknown">low</span>}
    </span>
  );
}
