import { describe, expect, it } from 'vitest';
import { INCIDENTS_REST_ROUTED } from './incidentsApi';

/**
 * M11 wired the gateway route for alert-service, closing finding G-4 for this screen.
 *
 * The guard that used to assert this flag was `false` has been inverted rather than deleted. It is
 * still doing a job: the flag is the single switch between "the screen queries the gateway" and
 * "the screen shows an awaiting-integration state", and a silent flip back to `false` would take
 * Incident Investigation off the air with no test failing.
 */
describe('M11 integration', () => {
  it('queries alert-service incidents through the gateway', () => {
    expect(INCIDENTS_REST_ROUTED).toBe(true);
  });
});
