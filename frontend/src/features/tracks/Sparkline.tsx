import { cn } from '@/lib/cn';

/**
 * A minimal telemetry sparkline. The stroke is deliberately **neutral** — this plots a scalar over
 * time (speed, altitude), which is orthogonal to threat, so it never borrows a threat hue (§2.1: colour
 * carries meaning only). A single point or an empty series renders a flat baseline rather than nothing.
 */
export function Sparkline({
  values,
  width = 160,
  height = 32,
  className,
  ariaLabel,
}: {
  values: readonly number[];
  width?: number;
  height?: number;
  className?: string;
  ariaLabel?: string;
}) {
  const pad = 2;
  const clean = values.filter((v) => Number.isFinite(v));
  const points = toPoints(clean, width, height, pad);
  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      className={cn('overflow-visible', className)}
      role="img"
      aria-label={ariaLabel}
      preserveAspectRatio="none"
    >
      {points.length >= 2 ? (
        <polyline
          points={points.map((p) => `${p.x},${p.y}`).join(' ')}
          fill="none"
          stroke="var(--color-muted-foreground)"
          strokeWidth={1.25}
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      ) : (
        <line
          x1={pad}
          y1={height / 2}
          x2={width - pad}
          y2={height / 2}
          stroke="var(--color-border-strong)"
          strokeWidth={1}
          strokeDasharray="2 3"
        />
      )}
    </svg>
  );
}

function toPoints(values: readonly number[], width: number, height: number, pad: number) {
  if (values.length === 0) {
    return [];
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const innerW = width - pad * 2;
  const innerH = height - pad * 2;
  const step = values.length > 1 ? innerW / (values.length - 1) : 0;
  return values.map((v, i) => ({
    x: pad + i * step,
    y: pad + innerH - ((v - min) / span) * innerH,
  }));
}
