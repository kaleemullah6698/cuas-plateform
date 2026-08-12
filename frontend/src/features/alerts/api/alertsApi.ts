import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The alert lifecycle API (ARCHITECTURE.md §4.1.8, §2.4.3 — the operator alert queue and its
 * acknowledge → assign → resolve workflow). Types mirror alert-service's {@code AlertResponse} and
 * request DTOs exactly (the wire contract).
 *
 * <p><b>M11 integration point.</b> The gateway routes only `/api/v1/auth|users|audit` today; alert-
 * service is on a bare path, not gateway-routed (M9 finding G-4). The <em>live raised-alert feed</em>
 * arrives over the WebSocket and is genuine now, but the authoritative queue (status, disposition, the
 * full lifecycle) and every mutation need the REST route, so they are gated behind {@link
 * ALERTS_REST_ROUTED}. The client, types, hooks and mutations are complete; M11 wires the route and
 * flips the flag. Until then the queue shows the live raised feed and the workflow actions are disabled
 * with a clear reason — never a request that cannot succeed, never fabricated status.
 */
export const ALERTS_REST_ROUTED = true;

export type AlertStatusName = 'RAISED' | 'ACKNOWLEDGED' | 'ESCALATED' | 'RESOLVED' | 'SUPPRESSED';

export const ALERT_DISPOSITIONS = [
  'TRUE_POSITIVE',
  'FALSE_POSITIVE',
  'BENIGN',
  'DUPLICATE',
  'INCONCLUSIVE',
] as const;
export type AlertDisposition = (typeof ALERT_DISPOSITIONS)[number];

/** Mirrors alert-service's {@code AlertResponse} DTO. */
export interface AlertRecord {
  alertId: string;
  reference: string;
  status: AlertStatusName;
  severity: string;
  recommendation: string;
  threatScore: number;
  trackId: string;
  sectorId: string;
  assessmentId: string | null;
  title: string;
  summary: string;
  raisedAt: string | null;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  escalationLevel: number;
  escalatesAt: string | null;
  occurrenceCount: number;
  incidentId: string | null;
  assignedTo: string | null;
}

export function useAlertQueue(enabled = true) {
  return useQuery({
    queryKey: ['alerts'],
    enabled: ALERTS_REST_ROUTED && enabled,
    queryFn: () => api.get<AlertRecord[]>('/api/v1/alerts?limit=500'),
  });
}

/** Acknowledge / assign / resolve, each invalidating the queue on success. The acting user is the JWT. */
export function useAlertActions() {
  const client = useQueryClient();
  const invalidate = () => client.invalidateQueries({ queryKey: ['alerts'] });

  const acknowledge = useMutation({
    mutationFn: ({ alertId, note }: { alertId: string; note?: string }) =>
      api.post<AlertRecord>(`/api/v1/alerts/${alertId}/acknowledge`, { note: note ?? '' }),
    onSuccess: invalidate,
  });
  const assign = useMutation({
    mutationFn: ({ alertId, assigneeId }: { alertId: string; assigneeId: string }) =>
      api.post<AlertRecord>(`/api/v1/alerts/${alertId}/assign`, { assigneeId }),
    onSuccess: invalidate,
  });
  const resolve = useMutation({
    mutationFn: ({ alertId, disposition, note }: { alertId: string; disposition: AlertDisposition; note?: string }) =>
      api.post<AlertRecord>(`/api/v1/alerts/${alertId}/resolve`, { disposition, note: note ?? '' }),
    onSuccess: invalidate,
  });

  return { acknowledge, assign, resolve };
}
