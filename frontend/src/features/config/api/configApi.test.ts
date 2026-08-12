import { describe, expect, it } from 'vitest';
import { CONFIG_REST_ROUTED } from './configApi';

/**
 * M11 wired the gateway route for config-service, closing finding G-4 for this screen.
 *
 * The guard that used to assert this flag was `false` has been inverted rather than deleted. It is
 * still doing a job: the flag is the single switch between "the screen queries the gateway" and
 * "the screen shows an awaiting-integration state", and a silent flip back to `false` would take
 * Configuration off the air with no test failing.
 */
describe('M11 integration', () => {
  it('queries config-service through the gateway', () => {
    expect(CONFIG_REST_ROUTED).toBe(true);
  });
});
