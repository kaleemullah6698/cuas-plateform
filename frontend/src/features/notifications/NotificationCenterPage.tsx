import { Bell, Send } from 'lucide-react';
import { Badge, Button, PageHeader, Panel, PanelBody, PanelHeader, PanelTitle, StatusDot } from '@/design-system';
import {
  CHANNELS,
  NOTIFICATIONS_REST_ROUTED,
  useNotificationActions,
  useNotificationHistory,
  usePreferences,
} from './api/notificationsApi';

/**
 * Notification Center (ARCHITECTURE.md §4.1.9): the caller's channel preferences and quiet hours, their
 * delivery history, and a test send — all scoped to the JWT subject (a user sees only their own).
 * Built against notification-service's real contract but gated until M11 routes it (G-4).
 */
export function NotificationCenterPage() {
  const preferences = usePreferences();
  const history = useNotificationHistory();
  const { sendTest } = useNotificationActions();
  const gated = !NOTIFICATIONS_REST_ROUTED;
  const channels = preferences.data?.channels ?? [];
  const records = history.data ?? [];

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={Bell} title="Notification Center">
        <Button size="sm" variant="secondary" disabled={gated || sendTest.isPending} onClick={() => sendTest.mutate()}>
          <Send className="size-3.5" /> Send test
        </Button>
      </PageHeader>
      {gated && (
        <p className="border-b border-border bg-elevated/40 px-4 py-1.5 text-2xs text-subtle-foreground">
          Preferences and delivery history arrive when the gateway routes notification-service (M11).
          Channels, quiet hours and the test send are built against the real contract.
        </p>
      )}

      <div className="grid flex-1 grid-cols-1 gap-3 overflow-auto p-3 lg:grid-cols-[320px_1fr]">
        <Panel>
          <PanelHeader>
            <PanelTitle>Channels</PanelTitle>
            {preferences.data && (
              <span className="text-2xs text-subtle-foreground">min {preferences.data.minSeverity}</span>
            )}
          </PanelHeader>
          <PanelBody className="space-y-1.5">
            {channels.length === 0 ? (
              <>
                <p className="text-xs text-subtle-foreground">
                  {gated ? 'Awaiting gateway route (M11).' : 'No channels configured.'}
                </p>
                <ul className="space-y-1 pt-1">
                  {CHANNELS.map((channel) => (
                    <li key={channel} className="flex items-center gap-2 text-xs text-subtle-foreground">
                      <StatusDot status="idle" /> {channel}
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              channels.map((channel) => (
                <div key={channel.channel} className="flex items-center justify-between gap-2 text-xs">
                  <span className="flex items-center gap-2">
                    <StatusDot status={channel.enabled ? 'ok' : 'idle'} />
                    <span className="font-medium">{channel.channel}</span>
                  </span>
                  <span className="truncate text-subtle-foreground">{channel.address}</span>
                </div>
              ))
            )}
          </PanelBody>
        </Panel>

        <Panel>
          <PanelHeader>
            <PanelTitle>Delivery history</PanelTitle>
            <span className="tnum text-2xs text-subtle-foreground">{records.length}</span>
          </PanelHeader>
          <PanelBody className="space-y-1">
            {records.length === 0 ? (
              <p className="text-xs text-subtle-foreground">
                {gated ? 'Awaiting gateway route (M11).' : 'No notifications yet.'}
              </p>
            ) : (
              records.map((record) => (
                <div key={record.notificationId} className="flex items-center gap-2 text-xs">
                  <Badge variant={record.status === 'SENT' ? 'ok' : record.status === 'FAILED' ? 'down' : 'neutral'}>
                    {record.status}
                  </Badge>
                  <span className="shrink-0 text-subtle-foreground">{record.channel}</span>
                  <span className="min-w-0 flex-1 truncate">{record.subject}</span>
                </div>
              ))
            )}
          </PanelBody>
        </Panel>
      </div>
    </div>
  );
}
