import { describe, expect, it } from 'vitest';
import { NOTIFICATIONS_REST_ROUTED } from './notificationsApi';

describe('M11 integration boundary', () => {
  it('keeps notifications gated until the gateway routes notification-service (G-4)', () => {
    expect(NOTIFICATIONS_REST_ROUTED).toBe(false);
  });
});
