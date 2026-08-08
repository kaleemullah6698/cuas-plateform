import { describe, expect, it } from 'vitest';
import type { AlertView } from '@/realtime/protocol';
import type { AlertRecord } from './api/alertsApi';
import { bucketOf, fromLiveAlert, fromRecord, groupAlerts, type QueueAlert } from './alertQueue';

function live(alertId: string, severity: string, raisedAt: string): AlertView {
  return { alertId, reference: `ALT-${alertId}`, severity, trackId: `trk-${alertId}`, title: alertId, summary: '', raisedAt };
}

function record(over: Partial<AlertRecord> & { alertId: string; status: AlertRecord['status'] }): AlertRecord {
  return {
    reference: 'ALT-9',
    severity: 'HIGH',
    recommendation: 'TRACK_AND_MONITOR',
    threatScore: 0.8,
    trackId: 'trk-9',
    sectorId: 'NE',
    assessmentId: null,
    title: 'x',
    summary: '',
    raisedAt: null,
    acknowledgedAt: null,
    resolvedAt: null,
    escalationLevel: 0,
    escalatesAt: null,
    occurrenceCount: 1,
    incidentId: null,
    assignedTo: null,
    ...over,
  };
}

describe('bucketOf', () => {
  it('maps lifecycle status to the three §2.4.3 buckets', () => {
    expect(bucketOf('RAISED')).toBe('unacknowledged');
    expect(bucketOf('ESCALATED')).toBe('unacknowledged');
    expect(bucketOf('ACKNOWLEDGED')).toBe('in_progress');
    expect(bucketOf('RESOLVED')).toBe('resolved');
    expect(bucketOf('SUPPRESSED')).toBe('resolved');
  });
});

describe('fromLiveAlert', () => {
  it('treats a live raised alert as unacknowledged and non-authoritative', () => {
    const q = fromLiveAlert(live('1', 'THREAT_LEVEL_CRITICAL', '2026-08-08T10:00:00Z'));
    expect(q.status).toBe('RAISED');
    expect(q.severity).toBe('critical');
    expect(q.authoritative).toBe(false);
  });
});

describe('fromRecord', () => {
  it('carries the authoritative status and normalises severity', () => {
    const q = fromRecord(record({ alertId: '2', status: 'ACKNOWLEDGED', severity: 'MEDIUM', assignedTo: 'u1' }));
    expect(q.status).toBe('ACKNOWLEDGED');
    expect(q.severity).toBe('medium');
    expect(q.assignedTo).toBe('u1');
    expect(q.authoritative).toBe(true);
  });
});

describe('groupAlerts', () => {
  it('buckets and orders each bucket by threat then recency', () => {
    const alerts: QueueAlert[] = [
      fromLiveAlert(live('low', 'THREAT_LEVEL_LOW', '2026-08-08T10:05:00Z')),
      fromLiveAlert(live('crit', 'THREAT_LEVEL_CRITICAL', '2026-08-08T10:00:00Z')),
      fromRecord(record({ alertId: 'ack', status: 'ACKNOWLEDGED' })),
      fromRecord(record({ alertId: 'done', status: 'RESOLVED' })),
    ];
    const groups = groupAlerts(alerts);
    expect(groups.unacknowledged.map((a) => a.alertId)).toEqual(['crit', 'low']);
    expect(groups.in_progress.map((a) => a.alertId)).toEqual(['ack']);
    expect(groups.resolved.map((a) => a.alertId)).toEqual(['done']);
  });
});
