import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The incident API (ARCHITECTURE.md §4.1.8, §2.4.3 — operator-declared investigations, their timeline,
 * notes, evidence and decision chain). Types mirror alert-service's {@code IncidentDtos} exactly.
 *
 * <p><b>M11 integration point.</b> Incidents are entirely REST, and alert-service is not gateway-routed
 * today (finding G-4), so the whole screen is gated behind {@link INCIDENTS_REST_ROUTED}: the client,
 * types, queries and mutations are complete; M11 wires the route and flips the flag. Every mutating
 * endpoint takes the acting user from the JWT (§2.4.3 — declaration and closure are human acts), so no
 * actor is ever sent in a body. The decision chain has no engagement-action field: the platform emits
 * advisory recommendations only (§0), and there is nowhere an engagement could be written.
 */
export const INCIDENTS_REST_ROUTED = true;

export type IncidentStatusName = 'OPEN' | 'UNDER_REVIEW' | 'CLOSED';

export interface DecisionChain {
  systemRecommended: string | null;
  operatorDecision: string;
  authorisedBy: string;
  authorisedByName: string;
  authorisedAt: string | null;
  auditRef: string;
  engagementAction: string;
}

export interface IncidentRecord {
  incidentId: string;
  reference: string;
  status: IncidentStatusName;
  severity: string;
  title: string;
  sectorId: string;
  trackIds: string[];
  alertIds: string[];
  declaredBy: string;
  declaredByName: string;
  assignedTo: string | null;
  assignedToName: string;
  openedAt: string | null;
  closedAt: string | null;
  closureDisposition: string | null;
  closureSummary: string;
  decisionChain: DecisionChain | null;
}

export interface TimelineEntry {
  eventId: string;
  sourceTopic: string;
  sourceId: string;
  summary: string;
  actorId: string | null;
  actorName: string;
  occurredAt: string | null;
}

export interface IncidentNote {
  noteId: string;
  authorId: string;
  authorName: string;
  body: string;
  createdAt: string | null;
}

export interface EvidenceRef {
  evidenceId: string;
  kind: string;
  uri: string;
  sha256: string;
  sizeBytes: number;
  contentType: string;
  sourceId: string;
  capturedAt: string | null;
}

const gated = (enabled: boolean) => INCIDENTS_REST_ROUTED && enabled;

export function useIncidents(status?: IncidentStatusName) {
  return useQuery({
    queryKey: ['incidents', status ?? 'all'],
    enabled: gated(true),
    queryFn: () =>
      api.get<IncidentRecord[]>(`/api/v1/incidents${status ? `?status=${status}` : ''}`),
  });
}

export function useIncident(incidentId: string | null) {
  return useQuery({
    queryKey: ['incident', incidentId],
    enabled: gated(!!incidentId),
    queryFn: () => api.get<IncidentRecord>(`/api/v1/incidents/${incidentId}`),
  });
}

export function useIncidentTimeline(incidentId: string | null) {
  return useQuery({
    queryKey: ['incident-timeline', incidentId],
    enabled: gated(!!incidentId),
    queryFn: () => api.get<TimelineEntry[]>(`/api/v1/incidents/${incidentId}/timeline`),
  });
}

export function useIncidentNotes(incidentId: string | null) {
  return useQuery({
    queryKey: ['incident-notes', incidentId],
    enabled: gated(!!incidentId),
    queryFn: () => api.get<IncidentNote[]>(`/api/v1/incidents/${incidentId}/notes`),
  });
}

export function useIncidentEvidence(incidentId: string | null) {
  return useQuery({
    queryKey: ['incident-evidence', incidentId],
    enabled: gated(!!incidentId),
    queryFn: () => api.get<EvidenceRef[]>(`/api/v1/incidents/${incidentId}/evidence`),
  });
}

/** The incident mutations. Each invalidates the affected queries; the acting user is the JWT subject. */
export function useIncidentActions() {
  const client = useQueryClient();
  const invalidate = (incidentId?: string) => {
    void client.invalidateQueries({ queryKey: ['incidents'] });
    if (incidentId) {
      void client.invalidateQueries({ queryKey: ['incident', incidentId] });
      void client.invalidateQueries({ queryKey: ['incident-timeline', incidentId] });
      void client.invalidateQueries({ queryKey: ['incident-notes', incidentId] });
    }
  };

  const declare = useMutation({
    mutationFn: ({ alertIds, title }: { alertIds: string[]; title: string }) =>
      api.post<IncidentRecord>('/api/v1/incidents', { alertIds, title }),
    onSuccess: () => invalidate(),
  });
  const addNote = useMutation({
    mutationFn: ({ incidentId, body }: { incidentId: string; body: string }) =>
      api.post<IncidentNote>(`/api/v1/incidents/${incidentId}/notes`, { body }),
    onSuccess: (_data, vars) => invalidate(vars.incidentId),
  });
  const close = useMutation({
    mutationFn: ({ incidentId, disposition, summary }: { incidentId: string; disposition: string; summary: string }) =>
      api.post<IncidentRecord>(`/api/v1/incidents/${incidentId}/close`, { disposition, summary }),
    onSuccess: (_data, vars) => invalidate(vars.incidentId),
  });

  return { declare, addNote, close };
}
