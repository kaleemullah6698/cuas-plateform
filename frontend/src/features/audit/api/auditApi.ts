import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The audit API (ARCHITECTURE.md §4.1.11, §7.5). Types mirror audit-service's {@code
 * AuditRecordResponse} and {@code ChainVerificationResponse} exactly. audit-service <em>is</em>
 * gateway-routed (`/api/v1/audit`), so this screen is genuinely live today. Read needs {@code
 * audit:read} and verify needs {@code audit:verify} — held only by AUDITOR and ADMIN.
 */
export interface AuditRecord {
  seq: number;
  auditId: string;
  occurredAt: string | null;
  recordedAt: string | null;
  actorId: string;
  actorType: string;
  actorIp: string | null;
  sessionId: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  outcome: string;
  details: Record<string, string> | null;
  correlationId: string | null;
  prevHash: string | null;
  recordHash: string | null;
}

export interface ChainVerification {
  intact: boolean;
  recordsVerified: number;
  headHash: string | null;
  brokenAtSeq: number | null;
  brokenAtAuditId: string | null;
  reason: string | null;
}

export interface AuditFilters {
  actorId?: string;
  action?: string;
  resourceType?: string;
  limit: number;
}

export function useAuditSearch(filters: AuditFilters) {
  return useQuery({
    queryKey: ['audit', filters],
    queryFn: () => api.get<AuditRecord[]>(`/api/v1/audit?${toQuery(filters)}`),
  });
}

export function useVerifyChain() {
  return useMutation({
    mutationFn: (range?: { fromSeq?: number; toSeq?: number }) => {
      const params = new URLSearchParams();
      if (range?.fromSeq != null) params.set('fromSeq', String(range.fromSeq));
      if (range?.toSeq != null) params.set('toSeq', String(range.toSeq));
      const query = params.toString();
      return api.post<ChainVerification>(`/api/v1/audit/verify${query ? `?${query}` : ''}`);
    },
  });
}

function toQuery(filters: AuditFilters): string {
  const params = new URLSearchParams();
  if (filters.actorId?.trim()) params.set('actorId', filters.actorId.trim());
  if (filters.action?.trim()) params.set('action', filters.action.trim());
  if (filters.resourceType?.trim()) params.set('resourceType', filters.resourceType.trim());
  params.set('limit', String(filters.limit));
  return params.toString();
}
