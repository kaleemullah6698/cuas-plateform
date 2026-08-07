import type { LucideIcon } from 'lucide-react';

/**
 * A workspace header for a module still being built out (M10.C–G). It keeps the shell navigable and
 * intentional while the feature groups land one at a time — not a "coming soon" toast, but the real
 * page chrome each module will fill.
 */
export function ModulePlaceholder({
  title,
  description,
  icon: Icon,
  milestone,
}: {
  title: string;
  description: string;
  icon: LucideIcon;
  milestone: string;
}) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-4">
        <Icon className="size-4 text-muted-foreground" strokeWidth={1.75} />
        <h1 className="text-sm font-semibold">{title}</h1>
      </div>
      <div className="grid flex-1 place-items-center p-6">
        <div className="max-w-md text-center">
          <p className="text-sm text-muted-foreground">{description}</p>
          <p className="mt-2 text-2xs uppercase tracking-wider text-subtle-foreground">
            Delivered in {milestone}
          </p>
        </div>
      </div>
    </div>
  );
}
