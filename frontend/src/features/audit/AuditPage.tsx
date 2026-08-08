import { ScrollText, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { useAuthStore } from '@/auth/authStore';
import { Badge, Button, Input, PageHeader } from '@/design-system';
import { cn } from '@/lib/cn';
import { type AuditFilters, useAuditSearch, useVerifyChain } from './api/auditApi';

/**
 * Audit Log Viewer (ARCHITECTURE.md §2 Screen 9, §4.1.11). Live today — audit-service is gateway-
 * routed. Query by actor / action / resource, and verify a segment of the tamper-evident hash chain.
 * A verification failure is the single highest-severity condition the UI can show: it renders a
 * full-width, non-dismissible critical banner naming the first broken link (§4.1.11).
 */
export function AuditPage() {
  const perms = useAuthStore((s) => s.perms);
  const canVerify = perms.includes('audit:verify');
  const [draft, setDraft] = useState<AuditFilters>({ actorId: '', action: '', resourceType: '', limit: 100 });
  const [applied, setApplied] = useState<AuditFilters>(draft);
  const search = useAuditSearch(applied);
  const verify = useVerifyChain();
  const rows = search.data ?? [];
  const result = verify.data;

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={ScrollText} title="Audit Log">
        {canVerify && (
          <Button size="sm" variant="secondary" disabled={verify.isPending} onClick={() => verify.mutate(undefined)}>
            <ShieldCheck className="size-3.5" /> Verify chain
          </Button>
        )}
      </PageHeader>

      {result && (
        <div
          role={result.intact ? 'status' : 'alert'}
          className={cn(
            'flex items-center gap-2 px-4 py-2 text-xs',
            result.intact
              ? 'border-b border-ok/30 bg-ok/10 text-ok'
              : 'border-b-2 border-critical bg-critical/15 font-semibold text-critical',
          )}
        >
          {result.intact ? (
            <span>Chain intact — {result.recordsVerified} records verified.</span>
          ) : (
            <span>
              AUDIT CHAIN BROKEN at seq {result.brokenAtSeq} ({result.brokenAtAuditId}) — {result.reason}
            </span>
          )}
        </div>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setApplied({ ...draft });
        }}
        className="flex flex-wrap items-center gap-2 border-b border-border px-4 py-2"
      >
        <Input
          value={draft.actorId}
          onChange={(e) => setDraft((d) => ({ ...d, actorId: e.target.value }))}
          placeholder="Actor"
          aria-label="Actor"
          className="h-8 w-40 text-xs"
        />
        <Input
          value={draft.action}
          onChange={(e) => setDraft((d) => ({ ...d, action: e.target.value }))}
          placeholder="Action e.g. LOGIN_SUCCEEDED"
          aria-label="Action"
          className="h-8 w-56 text-xs"
        />
        <Input
          value={draft.resourceType}
          onChange={(e) => setDraft((d) => ({ ...d, resourceType: e.target.value }))}
          placeholder="Resource type e.g. USER"
          aria-label="Resource type"
          className="h-8 w-44 text-xs"
        />
        <Button type="submit" size="sm" variant="secondary">Search</Button>
      </form>

      <div className="flex-1 overflow-auto p-3">
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full border-collapse text-xs">
            <thead className="sticky top-0 bg-card">
              <tr className="border-b border-border text-2xs uppercase tracking-wide text-subtle-foreground">
                {['Seq', 'When', 'Actor', 'Action', 'Resource', 'Outcome'].map((h) => (
                  <th key={h} className="px-2 py-1.5 text-left font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {search.isLoading ? (
                <Row>Loading…</Row>
              ) : search.isError ? (
                <Row warn>Could not query the audit log.</Row>
              ) : rows.length === 0 ? (
                <Row>No records match the query.</Row>
              ) : (
                rows.map((record) => (
                  <tr key={record.auditId} className="border-b border-border/60">
                    <td className="tnum px-2 py-1.5 text-subtle-foreground">{record.seq}</td>
                    <td className="tnum px-2 py-1.5 text-muted-foreground">{time(record.occurredAt)}</td>
                    <td className="px-2 py-1.5">{record.actorId}</td>
                    <td className="px-2 py-1.5 font-medium">{record.action}</td>
                    <td className="px-2 py-1.5 text-muted-foreground">
                      {record.resourceType}{record.resourceId ? ` · ${record.resourceId}` : ''}
                    </td>
                    <td className="px-2 py-1.5">
                      <Badge variant={outcomeVariant(record.outcome)}>{record.outcome}</Badge>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function Row({ children, warn }: { children: React.ReactNode; warn?: boolean }) {
  return (
    <tr>
      <td colSpan={6} className={cn('px-2 py-6 text-center text-sm', warn ? 'text-warn' : 'text-muted-foreground')}>
        {children}
      </td>
    </tr>
  );
}

function outcomeVariant(outcome: string): 'ok' | 'warn' | 'down' | 'neutral' {
  if (outcome === 'SUCCESS') return 'ok';
  if (outcome === 'DENIED') return 'warn';
  if (outcome === 'FAILURE') return 'down';
  return 'neutral';
}

function time(iso: string | null): string {
  if (!iso) return '——';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '——' : date.toISOString().replace('T', ' ').slice(0, 19);
}
