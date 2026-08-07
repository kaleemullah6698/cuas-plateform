import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/cn';

/**
 * A single command-centre statistic (§2.4.1). Dense and quiet: a small label, a large tabular value
 * that does not jitter as it streams, and an optional sub-line. Not a decorative card — a readout.
 */
export function KpiTile({
  label,
  value,
  sub,
  icon: Icon,
  className,
}: {
  label: string;
  value: React.ReactNode;
  sub?: React.ReactNode;
  icon?: LucideIcon;
  className?: string;
}) {
  return (
    <div className={cn('rounded-md border border-border bg-card px-3 py-2.5', className)}>
      <div className="flex items-center justify-between">
        <span className="text-2xs font-semibold uppercase tracking-wider text-subtle-foreground">
          {label}
        </span>
        {Icon && <Icon className="size-3.5 text-subtle-foreground" strokeWidth={1.75} />}
      </div>
      <div className="mt-1 text-xl font-semibold tabular-nums leading-none">{value}</div>
      {sub && <div className="mt-1 text-xs text-muted-foreground">{sub}</div>}
    </div>
  );
}
