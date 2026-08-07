import { NavLink } from 'react-router-dom';
import { cn } from '@/lib/cn';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/design-system';
import { NAV_SECTIONS } from './navigation';

/**
 * The left icon rail (ARCHITECTURE.md §2.4). Narrow and always present — a command centre keeps its
 * whole surface reachable in one click. The active section is marked with an accent spine; each icon
 * carries a tooltip so the rail stays narrow without becoming a guessing game.
 */
export function NavRail() {
  return (
    <nav
      aria-label="Primary"
      className="flex w-14 shrink-0 flex-col items-center gap-1 border-r border-border bg-surface py-2"
    >
      {NAV_SECTIONS.map((section) => (
        <Tooltip key={section.to} delayDuration={300}>
          <TooltipTrigger asChild>
            <NavLink
              to={section.to}
              aria-label={section.label}
              className={({ isActive }) =>
                cn(
                  'group relative flex size-10 items-center justify-center rounded-md transition-colors',
                  isActive
                    ? 'bg-elevated text-foreground'
                    : 'text-muted-foreground hover:bg-elevated hover:text-foreground',
                )
              }
            >
              {({ isActive }) => (
                <>
                  {isActive && (
                    <span className="absolute left-0 h-5 w-0.5 rounded-r-full bg-primary" aria-hidden />
                  )}
                  <section.icon className="size-4.5" strokeWidth={1.75} />
                </>
              )}
            </NavLink>
          </TooltipTrigger>
          <TooltipContent side="right">{section.label}</TooltipContent>
        </Tooltip>
      ))}
    </nav>
  );
}
