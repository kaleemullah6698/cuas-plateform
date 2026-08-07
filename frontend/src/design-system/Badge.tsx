import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/cn';

const badge = cva(
  'inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5 text-2xs font-semibold uppercase tracking-wide',
  {
    variants: {
      variant: {
        neutral: 'bg-elevated text-muted-foreground border border-border-strong',
        primary: 'bg-primary/15 text-primary border border-primary/30',
        critical: 'bg-critical/15 text-critical border border-critical/40',
        high: 'bg-high/15 text-high border border-high/40',
        medium: 'bg-medium/15 text-medium border border-medium/40',
        low: 'bg-low/15 text-low border border-low/40',
        unknown: 'bg-unknown/15 text-unknown border border-unknown/40',
        ok: 'bg-ok/15 text-ok border border-ok/40',
        warn: 'bg-warn/15 text-warn border border-warn/40',
        down: 'bg-down/15 text-down border border-down/40',
      },
    },
    defaultVariants: { variant: 'neutral' },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badge> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badge({ variant }), className)} {...props} />;
}
