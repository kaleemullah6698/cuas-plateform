import { FileText } from 'lucide-react';
import { useState } from 'react';
import { Button, PageHeader, Panel, PanelBody, PanelHeader, PanelTitle } from '@/design-system';
import {
  REPORT_FORMATS,
  REPORT_TYPES,
  REPORTS_REST_ROUTED,
  type ReportFormat,
  type ReportType,
  useGenerateReport,
  useReportTemplates,
} from './api/reportsApi';

const TYPE_LABEL: Record<ReportType, string> = {
  INCIDENT: 'Incident report',
  DAILY_SUMMARY: 'Daily summary',
  SENSOR_PERFORMANCE: 'Sensor performance',
  FUSION_EFFICACY: 'Fusion efficacy',
};

/**
 * Intelligence Reports (ARCHITECTURE.md §2 Screen 10, §4.1.10): produce a defensible written artefact
 * from system data — pick a template, set the format, generate asynchronously, download when ready.
 * Built against reporting-service's real contract but gated until M11 routes it (G-4); the async
 * generate → poll → download flow lights up with the route.
 */
export function ReportsPage() {
  const templates = useReportTemplates();
  const generate = useGenerateReport();
  const [type, setType] = useState<ReportType>('INCIDENT');
  const [format, setFormat] = useState<ReportFormat>('PDF');
  const gated = !REPORTS_REST_ROUTED;

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={FileText} title="Reports" />
      {gated && (
        <p className="border-b border-border bg-elevated/40 px-4 py-1.5 text-2xs text-subtle-foreground">
          Report generation arrives when the gateway routes reporting-service (M11). The template
          picker and the async generate → poll → download flow are built against the real contract.
        </p>
      )}

      <div className="grid flex-1 grid-cols-1 gap-3 overflow-auto p-3 lg:grid-cols-[1fr_320px]">
        <Panel>
          <PanelHeader>
            <PanelTitle>Templates</PanelTitle>
          </PanelHeader>
          <PanelBody className="space-y-1.5">
            {(templates.data?.templates ?? REPORT_TYPES.map((t) => ({ type: t, name: TYPE_LABEL[t], description: '', formats: REPORT_FORMATS }))).map(
              (template) => (
                <div key={template.type} className="rounded-sm border border-border px-2.5 py-1.5 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{template.name || TYPE_LABEL[template.type]}</span>
                    <span className="tnum text-2xs text-subtle-foreground">{template.formats.join(' · ')}</span>
                  </div>
                  {template.description && <p className="text-2xs text-subtle-foreground">{template.description}</p>}
                </div>
              ),
            )}
          </PanelBody>
        </Panel>

        <Panel>
          <PanelHeader>
            <PanelTitle>Generate</PanelTitle>
          </PanelHeader>
          <PanelBody className="space-y-2.5">
            <Labelled label="Template">
              <select
                value={type}
                onChange={(e) => setType(e.target.value as ReportType)}
                aria-label="Report template"
                className="w-full rounded-sm border border-input bg-surface px-1.5 py-1 text-xs text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {REPORT_TYPES.map((t) => (
                  <option key={t} value={t}>{TYPE_LABEL[t]}</option>
                ))}
              </select>
            </Labelled>
            <Labelled label="Format">
              <select
                value={format}
                onChange={(e) => setFormat(e.target.value as ReportFormat)}
                aria-label="Report format"
                className="w-full rounded-sm border border-input bg-surface px-1.5 py-1 text-xs text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {REPORT_FORMATS.map((f) => (
                  <option key={f} value={f}>{f}</option>
                ))}
              </select>
            </Labelled>
            <Button
              size="sm"
              className="w-full"
              disabled={gated || generate.isPending}
              onClick={() => generate.mutate({ type, format })}
            >
              Generate report
            </Button>
            <p className="text-2xs text-subtle-foreground">
              {gated ? 'Awaiting gateway route (M11).' : 'Generation runs asynchronously; poll and download when ready.'}
            </p>
          </PanelBody>
        </Panel>
      </div>
    </div>
  );
}

function Labelled({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <span className="text-2xs uppercase tracking-wide text-subtle-foreground">{label}</span>
      {children}
    </div>
  );
}
