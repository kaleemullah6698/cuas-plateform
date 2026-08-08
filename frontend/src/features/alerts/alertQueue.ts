import { type Severity, severityOf, severityRank } from '@/lib/severity';
import type { AlertView } from '@/realtime/protocol';
import type { AlertRecord, AlertStatusName } from './api/alertsApi';

/**
 * The Alert Center's queue logic (ARCHITECTURE.md §2.4.3, Screen 7). Three buckets — Unacknowledged /
 * In Progress / Resolved — so no alert is missed and every alert has an owner and an outcome. Pure
 * functions over either the authoritative REST record or the live raised-alert feed, so the bucketing
 * and ordering are unit-testable without a store or a render.
 */
export type AlertBucket = 'unacknowledged' | 'in_progress' | 'resolved';

export const ALERT_BUCKETS: AlertBucket[] = ['unacknowledged', 'in_progress', 'resolved'];

export const ALERT_BUCKET_LABEL: Record<AlertBucket, string> = {
  unacknowledged: 'Unacknowledged',
  in_progress: 'In Progress',
  resolved: 'Resolved',
};

/** A raised or escalated alert has not been attended to; acknowledged is being worked; the rest are done. */
export function bucketOf(status: AlertStatusName): AlertBucket {
  switch (status) {
    case 'RAISED':
    case 'ESCALATED':
      return 'unacknowledged';
    case 'ACKNOWLEDGED':
      return 'in_progress';
    case 'RESOLVED':
    case 'SUPPRESSED':
      return 'resolved';
  }
}

/** The normalised shape the queue renders, from either data source. */
export interface QueueAlert {
  alertId: string;
  reference: string;
  severity: Severity;
  status: AlertStatusName;
  trackId: string;
  title: string;
  raisedAt: string | null;
  assignedTo: string | null;
  incidentId: string | null;
  /** True when the row came from the authoritative REST record rather than the live raised feed. */
  authoritative: boolean;
}

export function fromRecord(record: AlertRecord): QueueAlert {
  return {
    alertId: record.alertId,
    reference: record.reference,
    severity: severityOf(record.severity),
    status: record.status,
    trackId: record.trackId,
    title: record.title,
    raisedAt: record.raisedAt,
    assignedTo: record.assignedTo,
    incidentId: record.incidentId,
    authoritative: true,
  };
}

/**
 * A live alert from the WebSocket feed. We know only that it was raised — the stream carries no
 * lifecycle status — so it is bucketed as Unacknowledged. The authoritative status arrives with the
 * REST queue in M11; until then this is honest: a raised alert we have not yet confirmed as handled.
 */
export function fromLiveAlert(view: AlertView): QueueAlert {
  return {
    alertId: view.alertId,
    reference: view.reference,
    severity: severityOf(view.severity),
    status: 'RAISED',
    trackId: view.trackId,
    title: view.title,
    raisedAt: view.raisedAt,
    assignedTo: null,
    incidentId: null,
    authoritative: false,
  };
}

/** Group into the three buckets, each ordered by threat descending then most-recently-raised first. */
export function groupAlerts(alerts: readonly QueueAlert[]): Record<AlertBucket, QueueAlert[]> {
  const groups: Record<AlertBucket, QueueAlert[]> = {
    unacknowledged: [],
    in_progress: [],
    resolved: [],
  };
  for (const alert of alerts) {
    groups[bucketOf(alert.status)].push(alert);
  }
  for (const bucket of ALERT_BUCKETS) {
    groups[bucket].sort((a, b) => {
      const byThreat = severityRank(b.severity) - severityRank(a.severity);
      return byThreat !== 0 ? byThreat : raisedMs(b) - raisedMs(a);
    });
  }
  return groups;
}

function raisedMs(alert: QueueAlert): number {
  if (!alert.raisedAt) {
    return 0;
  }
  const ms = Date.parse(alert.raisedAt);
  return Number.isNaN(ms) ? 0 : ms;
}
