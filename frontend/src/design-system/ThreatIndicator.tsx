import { cn } from '@/lib/cn';
import { type Severity, SEVERITY_SHAPE, SEVERITY_SHORT } from '@/lib/severity';

const textColor: Record<Severity, string> = {
  critical: 'text-critical',
  high: 'text-high',
  medium: 'text-medium',
  low: 'text-low',
  unknown: 'text-unknown',
};

/**
 * A threat-state token carrying all three redundant channels the spec requires (§2.1.7,
 * add-frontend-feature skill): colour, a distinct shape, and a text label. Never colour alone — an
 * operator with colour-vision deficiency reads the shape and the word.
 */
export function ThreatIndicator({
  severity,
  label = true,
  className,
}: {
  severity: Severity;
  label?: boolean;
  className?: string;
}) {
  return (
    <span className={cn('inline-flex items-center gap-1 font-semibold', textColor[severity], className)}>
      <span aria-hidden="true">{SEVERITY_SHAPE[severity]}</span>
      {label && <span className="text-2xs tracking-wide">{SEVERITY_SHORT[severity]}</span>}
      <span className="sr-only">{SEVERITY_SHORT[severity]} threat</span>
    </span>
  );
}
