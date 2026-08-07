import { cn } from '@/lib/cn';

export type Status = 'ok' | 'warn' | 'down' | 'info' | 'idle';

const colors: Record<Status, string> = {
  ok: 'bg-ok',
  warn: 'bg-warn',
  down: 'bg-down',
  info: 'bg-info',
  idle: 'bg-unknown',
};

/** A small status indicator. Pulses only for a live/active state, per the motion rule. */
export function StatusDot({
  status,
  pulse = false,
  className,
}: {
  status: Status;
  pulse?: boolean;
  className?: string;
}) {
  return (
    <span className={cn('relative inline-flex size-2 shrink-0', className)} aria-hidden="true">
      {pulse && (
        <span
          className={cn('absolute inline-flex size-full animate-ping rounded-full opacity-60', colors[status])}
        />
      )}
      <span className={cn('relative inline-flex size-2 rounded-full', colors[status])} />
    </span>
  );
}
