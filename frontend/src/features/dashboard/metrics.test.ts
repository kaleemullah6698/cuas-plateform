import { describe, expect, it } from 'vitest';
import type { AlertView, SensorView } from '@/realtime/protocol';
import { severityCounts, sensorCounts, worstAlertSeverity } from './metrics';

function alert(severity: string): AlertView {
  return { alertId: severity, reference: 'ALT', severity, trackId: 't', title: 't', summary: 's', raisedAt: null };
}
function sensor(status: string): SensorView {
  return { sensorId: status, status, lastSeen: null };
}

describe('dashboard metrics', () => {
  it('counts alerts by normalised severity', () => {
    const counts = severityCounts([
      alert('THREAT_LEVEL_CRITICAL'),
      alert('THREAT_LEVEL_CRITICAL'),
      alert('THREAT_LEVEL_HIGH'),
      alert('SOMETHING_ELSE'),
    ]);
    expect(counts.critical).toBe(2);
    expect(counts.high).toBe(1);
    expect(counts.unknown).toBe(1);
  });

  it('reports the worst active severity, or null when empty', () => {
    expect(worstAlertSeverity([alert('THREAT_LEVEL_LOW'), alert('THREAT_LEVEL_HIGH')])).toBe('high');
    expect(worstAlertSeverity([])).toBeNull();
  });

  it('buckets sensors by status', () => {
    const counts = sensorCounts([sensor('ONLINE'), sensor('DEGRADED'), sensor('OFFLINE'), sensor('ONLINE')]);
    expect(counts).toEqual({ online: 2, degraded: 1, offline: 1, total: 4 });
  });
});
