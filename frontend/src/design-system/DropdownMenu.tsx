import * as Primitive from '@radix-ui/react-dropdown-menu';
import { forwardRef } from 'react';
import { cn } from '@/lib/cn';

export const DropdownMenu = Primitive.Root;
export const DropdownMenuTrigger = Primitive.Trigger;
export const DropdownMenuGroup = Primitive.Group;

export const DropdownMenuContent = forwardRef<
  React.ElementRef<typeof Primitive.Content>,
  React.ComponentPropsWithoutRef<typeof Primitive.Content>
>(({ className, sideOffset = 6, align = 'end', ...props }, ref) => (
  <Primitive.Portal>
    <Primitive.Content
      ref={ref}
      sideOffset={sideOffset}
      align={align}
      className={cn(
        'z-50 min-w-44 overflow-hidden rounded-md border border-border-strong bg-popover p-1 text-sm shadow-pop',
        className,
      )}
      {...props}
    />
  </Primitive.Portal>
));
DropdownMenuContent.displayName = 'DropdownMenuContent';

export const DropdownMenuItem = forwardRef<
  React.ElementRef<typeof Primitive.Item>,
  React.ComponentPropsWithoutRef<typeof Primitive.Item> & { destructive?: boolean }
>(({ className, destructive = false, ...props }, ref) => (
  <Primitive.Item
    ref={ref}
    className={cn(
      'flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none',
      'data-[highlighted]:bg-elevated data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      destructive ? 'text-critical data-[highlighted]:bg-critical/10' : 'text-foreground',
      className,
    )}
    {...props}
  />
));
DropdownMenuItem.displayName = 'DropdownMenuItem';

export function DropdownMenuLabel({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('px-2 py-1.5 text-2xs font-semibold uppercase tracking-wider text-subtle-foreground', className)}
      {...props}
    />
  );
}

export const DropdownMenuSeparator = () => <Primitive.Separator className="my-1 h-px bg-border" />;
