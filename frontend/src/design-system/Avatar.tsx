import { cn } from '@/lib/cn';

/** Initials avatar — no external image dependency, deterministic colour from the name. */
export function Avatar({ name, className }: { name: string; className?: string }) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
  return (
    <span
      className={cn(
        'inline-flex size-7 shrink-0 items-center justify-center rounded-full border border-border-strong',
        'bg-elevated text-2xs font-semibold text-foreground',
        className,
      )}
      aria-hidden="true"
    >
      {initials || '?'}
    </span>
  );
}
