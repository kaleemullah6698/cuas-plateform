import { describe, expect, it } from 'vitest';
import { CONFIG_REST_ROUTED } from './configApi';

describe('M11 integration boundary', () => {
  it('keeps configuration gated until the gateway routes config-service (G-4)', () => {
    expect(CONFIG_REST_ROUTED).toBe(false);
  });
});
