import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { forwardRef } from 'react';
import { cn } from '@/lib/cn';
import { Spinner } from './Spinner';

const button = cva(
  'inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-medium ' +
    'transition-colors duration-[120ms] focus-visible:outline-none disabled:pointer-events-none ' +
    'disabled:opacity-50 select-none',
  {
    variants: {
      variant: {
        primary: 'bg-primary text-primary-foreground hover:bg-primary-hover',
        secondary: 'bg-elevated text-foreground border border-border-strong hover:bg-popover',
        ghost: 'text-muted-foreground hover:bg-elevated hover:text-foreground',
        outline: 'border border-border-strong text-foreground hover:bg-elevated',
        destructive: 'bg-destructive text-destructive-foreground hover:brightness-110',
      },
      size: {
        sm: 'h-7 px-2.5 text-xs',
        md: 'h-8 px-3 text-sm',
        lg: 'h-9 px-4 text-base',
        icon: 'h-8 w-8',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof button> {
  asChild?: boolean;
  loading?: boolean;
}

/** The one button. Variants and sizes are the only knobs, so the console stays visually consistent. */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, loading = false, children, disabled, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button';
    return (
      <Comp
        ref={ref}
        className={cn(button({ variant, size }), className)}
        disabled={disabled ?? loading}
        {...props}
      >
        {/* Slot requires a single child, so the spinner is only injected on a real <button>. */}
        {loading && !asChild && <Spinner className="size-3.5" />}
        {children}
      </Comp>
    );
  },
);
Button.displayName = 'Button';
