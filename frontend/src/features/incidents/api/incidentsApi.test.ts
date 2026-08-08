import { describe, expect, it } from 'vitest';
import { INCIDENTS_REST_ROUTED } from './incidentsApi';

describe('M11 integration boundary', () => {
  it('keeps incidents gated until the gateway routes alert-service (M9 finding G-4)', () => {
    // M11 flips this to true when the route lands; until then the screen shows an "awaiting M11"
    // state instead of firing incident requests that cannot succeed.
    expect(INCIDENTS_REST_ROUTED).toBe(false);
  });
});
