import { ChevronDown, LogOut, Radio, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRealtime } from '@/app/RealtimeContext';
import { useAuthStore } from '@/auth/authStore';
import {
  Avatar,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  StatusDot,
  type Status,
} from '@/design-system';
import { useConnection } from '@/realtime/hooks';

/**
 * The command-centre top bar (ARCHITECTURE.md §2.4): identity of the system, the authoritative UTC
 * clock, the live connection state, and the operator's account. Kept quiet — a top bar an operator
 * stops noticing is one that is not competing with the airspace for attention.
 */
export function TopBar() {
  const realtime = useRealtime();
  const connection = useConnection(realtime.connection);
  const displayName = useAuthStore((s) => s.displayName);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  const status: Status =
    connection.status === 'CONNECTED' ? (connection.degraded ? 'warn' : 'ok') : 'down';
  const statusLabel =
    connection.status === 'CONNECTED' ? (connection.degraded ? 'DEGRADED' : 'LIVE') : 'OFFLINE';

  function signOut() {
    realtime.disconnect();
    clear();
    navigate('/login');
  }

  return (
    <header className="flex h-11 shrink-0 items-center justify-between border-b border-border bg-surface px-3">
      <div className="flex items-center gap-2.5">
        <div className="flex size-6 items-center justify-center rounded-sm bg-primary/15 text-primary">
          <Radio className="size-3.5" />
        </div>
        <div className="leading-none">
          <div className="text-sm font-semibold tracking-tight">C-UAS Command</div>
          <div className="text-2xs uppercase tracking-wider text-subtle-foreground">
            Counter-UAS Operations
          </div>
        </div>
      </div>

      <div className="flex items-center gap-4">
        <div className="flex items-center gap-1.5 text-xs">
          <StatusDot status={status} pulse={status === 'ok'} />
          <span className="font-medium text-muted-foreground">{statusLabel}</span>
        </div>
        <Clock />
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label="Account menu"
            className="flex items-center gap-1.5 rounded-md px-1 py-0.5 text-sm outline-none hover:bg-elevated"
          >
            <Avatar name={displayName} />
            <span className="hidden text-muted-foreground sm:inline">{displayName}</span>
            <ChevronDown className="size-3.5 text-subtle-foreground" />
          </DropdownMenuTrigger>
          <DropdownMenuContent>
            <DropdownMenuLabel>Signed in as {displayName}</DropdownMenuLabel>
            <DropdownMenuItem onSelect={() => navigate('/profile')}>
              <User className="size-4" /> Profile
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem destructive onSelect={signOut}>
              <LogOut className="size-4" /> Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}

function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return (
    <time className="tnum text-xs text-muted-foreground" dateTime={now.toISOString()}>
      {now.toISOString().slice(11, 19)}Z
    </time>
  );
}
