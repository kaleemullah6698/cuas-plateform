import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The reporting API (ARCHITECTURE.md §4.1.10). Types mirror reporting-service's {@code ReportDtos}.
 * <b>M11 integration point:</b> reporting-service is on a bare path, not gateway-routed (G-4), so the
 * queries and the generate mutation are gated behind {@link REPORTS_REST_ROUTED}. Built and typed
 * against the real contract; M11 wires the route and flips the flag.
 */
export const REPORTS_REST_ROUTED = true;

export const REPORT_TYPES = ['INCIDENT', 'DAILY_SUMMARY', 'SENSOR_PERFORMANCE', 'FUSION_EFFICACY'] as const;
export type ReportType = (typeof REPORT_TYPES)[number];
export const REPORT_FORMATS = ['PDF', 'JSON', 'CSV'] as const;
export type ReportFormat = (typeof REPORT_FORMATS)[number];

export interface ReportTemplate {
  type: ReportType;
  name: string;
  description: string;
  formats: ReportFormat[];
}

export interface ReportJob {
  reportId: string;
  type: ReportType;
  format: ReportFormat;
  status: string;
  failureReason: string | null;
  detail: string | null;
  rowCount: number;
  artifactUri: string | null;
  sha256: string | null;
  sizeBytes: number | null;
  generationMs: number | null;
  createdAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export function useReportTemplates() {
  return useQuery({
    queryKey: ['report-templates'],
    enabled: REPORTS_REST_ROUTED,
    queryFn: () => api.get<{ templates: ReportTemplate[] }>('/api/v1/reports/templates'),
  });
}

export function useGenerateReport() {
  return useMutation({
    mutationFn: (body: { type: ReportType; format: ReportFormat; parameters?: Record<string, unknown> }) =>
      api.post<ReportJob>('/api/v1/reports/generate', { ...body, parameters: body.parameters ?? {} }),
  });
}

/** The artifact download URL for a succeeded job (report:read); used as an anchor href in M11. */
export function reportDownloadUrl(reportId: string): string {
  return `/api/v1/reports/${reportId}/download`;
}
