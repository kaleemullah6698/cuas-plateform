import { zodResolver } from '@hookform/resolvers/zod';
import { ShieldAlert } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Badge, Button, Panel, PanelBody, PanelHeader, PanelTitle, PageHeader } from '@/design-system';
import { cn } from '@/lib/cn';
import { severityOf } from '@/lib/severity';
import { ThreatIndicator } from '@/design-system';
import {
  INCIDENTS_REST_ROUTED,
  type IncidentNote,
  useIncident,
  useIncidentActions,
  useIncidentEvidence,
  useIncidentNotes,
  useIncidents,
  useIncidentTimeline,
} from './api/incidentsApi';

const TABS = ['Overview', 'Timeline', 'Notes', 'Evidence', 'Decision Chain'] as const;
type Tab = (typeof TABS)[number];

/**
 * Incident Investigation (ARCHITECTURE.md §2.4.3, Screen 8): reconstruct a complete, defensible account
 * of what happened and what was decided. Incidents are entirely REST and alert-service is not gateway-
 * routed yet (finding G-4), so the whole screen is the M11 integration point — the list, the tabbed
 * detail (overview, timeline, notes, evidence, decision chain) and the note/close mutations are built
 * against the real contracts but gated behind {@link INCIDENTS_REST_ROUTED}, showing a clear "awaiting
 * M11" state rather than fabricated investigations.
 */
export function IncidentsPage() {
  const incidents = useIncidents();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('Overview');
  const rows = incidents.data ?? [];

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={ShieldAlert} title="Incidents" />

      {!INCIDENTS_REST_ROUTED && (
        <p className="border-b border-border bg-elevated/40 px-4 py-1.5 text-2xs text-subtle-foreground">
          Investigations arrive when the gateway routes alert-service (M11). The workflow — declare,
          timeline, notes, evidence, decision chain, close — is built and wired against the real
          contracts; it lights up with the route.
        </p>
      )}

      <div className="grid flex-1 grid-cols-1 gap-3 overflow-hidden p-3 lg:grid-cols-[300px_1fr]">
        <Panel className="flex min-h-0 flex-col">
          <PanelHeader>
            <PanelTitle>Investigations</PanelTitle>
            <span className="tnum text-2xs text-subtle-foreground">{rows.length}</span>
          </PanelHeader>
          <PanelBody className="min-h-0 flex-1 space-y-1 overflow-auto p-1.5">
            {rows.length === 0 ? (
              <p className="p-2 text-xs text-subtle-foreground">
                {INCIDENTS_REST_ROUTED ? 'No incidents declared.' : 'Awaiting gateway route (M11).'}
              </p>
            ) : (
              rows.map((incident) => (
                <button
                  key={incident.incidentId}
                  type="button"
                  onClick={() => setSelectedId(incident.incidentId)}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-sm border border-transparent px-2 py-1.5 text-left text-xs hover:bg-elevated/60',
                    incident.incidentId === selectedId && 'border-border-strong bg-elevated',
                  )}
                >
                  <ThreatIndicator severity={severityOf(incident.severity)} label={false} />
                  <span className="tnum shrink-0 text-subtle-foreground">{incident.reference}</span>
                  <span className="min-w-0 flex-1 truncate">{incident.title}</span>
                </button>
              ))
            )}
          </PanelBody>
        </Panel>

        <Panel className="flex min-h-0 flex-col">
          {selectedId ? (
            <>
              <div className="flex shrink-0 gap-1 border-b border-border px-2 py-1.5">
                {TABS.map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setTab(t)}
                    className={cn(
                      'rounded-sm px-2 py-1 text-xs',
                      t === tab ? 'bg-elevated text-foreground' : 'text-muted-foreground hover:bg-elevated/60',
                    )}
                  >
                    {t}
                  </button>
                ))}
              </div>
              <div className="min-h-0 flex-1 overflow-auto p-3">
                <IncidentTab incidentId={selectedId} tab={tab} />
              </div>
            </>
          ) : (
            <div className="grid h-full place-items-center p-6 text-center text-sm text-muted-foreground">
              Select an investigation to review its timeline, evidence and decision chain.
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}

function IncidentTab({ incidentId, tab }: { incidentId: string; tab: Tab }) {
  const incident = useIncident(incidentId);
  const timeline = useIncidentTimeline(incidentId);
  const notes = useIncidentNotes(incidentId);
  const evidence = useIncidentEvidence(incidentId);

  if (tab === 'Overview') {
    const data = incident.data;
    if (!data) {
      return <Awaiting />;
    }
    return (
      <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
        <Field label="Reference"><span className="tnum">{data.reference}</span></Field>
        <Field label="Status"><Badge variant="neutral">{data.status}</Badge></Field>
        <Field label="Severity"><ThreatIndicator severity={severityOf(data.severity)} /></Field>
        <Field label="Sector">{data.sectorId || '—'}</Field>
        <Field label="Declared by">{data.declaredByName || data.declaredBy}</Field>
        <Field label="Assigned to">{data.assignedToName || data.assignedTo || 'Unassigned'}</Field>
        <Field label="Tracks"><span className="tnum">{data.trackIds.length}</span></Field>
        <Field label="Alerts"><span className="tnum">{data.alertIds.length}</span></Field>
      </dl>
    );
  }

  if (tab === 'Timeline') {
    const entries = timeline.data ?? [];
    if (entries.length === 0) {
      return <Awaiting />;
    }
    return (
      <ol className="space-y-2">
        {entries.map((entry) => (
          <li key={entry.eventId} className="flex gap-3 text-xs">
            <span className="tnum shrink-0 text-subtle-foreground">{time(entry.occurredAt)}</span>
            <span className="shrink-0 font-medium">{entry.actorName || entry.actorId || 'SYS'}</span>
            <span className="text-muted-foreground">{entry.summary}</span>
          </li>
        ))}
      </ol>
    );
  }

  if (tab === 'Notes') {
    return <NotesTab incidentId={incidentId} notes={notes.data ?? []} />;
  }

  if (tab === 'Evidence') {
    const items = evidence.data ?? [];
    if (items.length === 0) {
      return <Awaiting note="Evidence references (with content hashes) appear here (M11)." />;
    }
    return (
      <ul className="space-y-1 text-xs">
        {items.map((item) => (
          <li key={item.evidenceId} className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-2">
              <Badge variant="neutral">{item.kind}</Badge>
              <span className="truncate">{item.uri}</span>
            </span>
            <span className="tnum text-subtle-foreground">{item.sha256.slice(0, 10)}…</span>
          </li>
        ))}
      </ul>
    );
  }

  // Decision Chain
  const chain = incident.data?.decisionChain;
  if (!chain) {
    return <Awaiting note="The decision chain — what was advised, decided and authorised — appears here (M11)." />;
  }
  return (
    <dl className="space-y-2 text-xs">
      <Field label="System recommended">{chain.systemRecommended ?? '—'}</Field>
      <Field label="Operator decision">{chain.operatorDecision}</Field>
      <Field label="Authorised by">{chain.authorisedByName || chain.authorisedBy}</Field>
      <Field label="Engagement action">
        <Badge variant="neutral">{chain.engagementAction}</Badge>
        <span className="ml-2 text-subtle-foreground">outside system scope</span>
      </Field>
      <Field label="Audit reference"><span className="tnum">{chain.auditRef}</span></Field>
    </dl>
  );
}

const noteSchema = z.object({ body: z.string().trim().min(1, 'A note cannot be empty').max(4000) });

function NotesTab({ incidentId, notes }: { incidentId: string; notes: IncidentNote[] }) {
  const { addNote } = useIncidentActions();
  const gated = !INCIDENTS_REST_ROUTED;
  const form = useForm<{ body: string }>({ resolver: zodResolver(noteSchema), defaultValues: { body: '' } });

  const onSubmit = form.handleSubmit((values) => {
    addNote.mutate({ incidentId, body: values.body }, { onSuccess: () => form.reset() });
  });

  return (
    <div className="space-y-3">
      <form onSubmit={onSubmit} className="space-y-1.5">
        <textarea
          {...form.register('body')}
          rows={2}
          disabled={gated}
          placeholder={gated ? 'Notes can be added once the route lands (M11)' : 'Add an investigative note…'}
          className="w-full resize-y rounded-md border border-input bg-surface px-2.5 py-1.5 text-xs text-foreground placeholder:text-subtle-foreground focus-visible:border-ring focus-visible:outline-none disabled:opacity-50"
        />
        <div className="flex items-center justify-between">
          <span className="text-2xs text-critical">{form.formState.errors.body?.message}</span>
          <Button type="submit" size="sm" disabled={gated || addNote.isPending}>
            Add note
          </Button>
        </div>
      </form>
      {notes.length === 0 ? (
        <Awaiting note="Operator notes are append-only and attributed — part of the exported record (M11)." />
      ) : (
        <ul className="space-y-2">
          {notes.map((note) => (
            <li key={note.noteId} className="rounded-sm border border-border px-2.5 py-1.5 text-xs">
              <div className="mb-0.5 flex items-center justify-between text-2xs text-subtle-foreground">
                <span className="font-medium text-muted-foreground">{note.authorName || note.authorId}</span>
                <span className="tnum">{time(note.createdAt)}</span>
              </div>
              <p className="whitespace-pre-wrap">{note.body}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-2xs uppercase tracking-wide text-subtle-foreground">{label}</dt>
      <dd className="text-foreground">{children}</dd>
    </div>
  );
}

function Awaiting({ note = 'Awaiting gateway route for alert-service (M11).' }: { note?: string }) {
  return <p className="text-xs text-subtle-foreground">{note}</p>;
}

function time(iso: string | null): string {
  if (!iso) {
    return '——';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '——' : date.toISOString().slice(11, 19);
}
