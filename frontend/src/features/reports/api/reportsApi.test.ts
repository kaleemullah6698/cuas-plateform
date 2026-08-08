import { describe, expect, it } from 'vitest';
import { REPORTS_REST_ROUTED } from './reportsApi';

describe('M11 integration boundary', () => {
  it('keeps reports gated until the gateway routes reporting-service (G-4)', () => {
    expect(REPORTS_REST_ROUTED).toBe(false);
  });
});
