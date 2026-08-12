import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The notification self-service API (ARCHITECTURE.md §4.1.9). Types mirror notification-service's
 * {@code PreferenceDtos} and {@code NotificationResponse}. Every operation is scoped to the caller
 * (the JWT subject) — a user reads and writes only their own preferences and history, which is why the
 * endpoints carry authentication rather than a §7.4 permission. <b>M11 integration point:</b>
 * notification-service is on a bare path, not gateway-routed (G-4), gated behind {@link
 * NOTIFICATIONS_REST_ROUTED}.
 */
export const NOTIFICATIONS_REST_ROUTED = true;

export const CHANNELS = ['WEBSOCKET', 'EMAIL', 'SMS', 'WEBHOOK'] as const;
export type Channel = (typeof CHANNELS)[number];

export interface ChannelView {
  channel: Channel;
  address: string;
  enabled: boolean;
  priority: number;
}

export interface Preferences {
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  minSeverity: string;
  channels: ChannelView[];
}

export interface NotificationRecord {
  notificationId: string;
  channel: Channel;
  trigger: string;
  urgency: string;
  status: string;
  subject: string;
  attempt: number;
  alertId: string | null;
  incidentId: string | null;
  trackId: string | null;
  reason: string | null;
  createdAt: string | null;
  sentAt: string | null;
  failedAt: string | null;
}

export function usePreferences() {
  return useQuery({
    queryKey: ['notification-preferences'],
    enabled: NOTIFICATIONS_REST_ROUTED,
    queryFn: () => api.get<Preferences>('/api/v1/notifications/preferences'),
  });
}

export function useNotificationHistory() {
  return useQuery({
    queryKey: ['notification-history'],
    enabled: NOTIFICATIONS_REST_ROUTED,
    queryFn: () => api.get<NotificationRecord[]>('/api/v1/notifications/history?limit=100'),
  });
}

export function useNotificationActions() {
  const client = useQueryClient();
  const sendTest = useMutation({
    mutationFn: () => api.post<NotificationRecord>('/api/v1/notifications/test'),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notification-history'] }),
  });
  return { sendTest };
}
