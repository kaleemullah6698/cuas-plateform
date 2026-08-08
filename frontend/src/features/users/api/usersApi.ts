import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * User and role administration (ARCHITECTURE.md §4.1.3, §7.4). Types mirror user-service's
 * {@code UserResponse} and request DTOs exactly. user-service <em>is</em> gateway-routed
 * (`/api/v1/users`), so this screen is genuinely live today — unlike the bare-path services (G-4).
 *
 * <p>The server is the authority on every rule the §7.4 matrix defines — self-escalation is refused
 * with {@code 403 SELF_ESCALATION_DENIED}, separation of duty with {@code 409}, and each change is
 * audited fail-closed. The UI gates the same actions on the caller's permissions for convenience, but
 * never relies on it: a refusal surfaces as the server's error.
 */
export const ROLES = ['VIEWER', 'OPERATOR', 'ANALYST', 'ENGINEER', 'AUDITOR', 'ADMIN'] as const;
export type Role = (typeof ROLES)[number];

/** Mirrors user-service's {@code UserResponse} DTO. */
export interface UserRecord {
  id: string;
  username: string;
  email: string;
  displayName: string;
  status: 'ACTIVE' | 'DEACTIVATED';
  roles: string[];
  permissions: string[];
  createdAt: string | null;
}

export function useUsers() {
  return useQuery({
    queryKey: ['users'],
    queryFn: () => api.get<UserRecord[]>('/api/v1/users?limit=200'),
  });
}

export function useUserActions() {
  const client = useQueryClient();
  const invalidate = () => client.invalidateQueries({ queryKey: ['users'] });

  const create = useMutation({
    mutationFn: (body: { username: string; email: string; displayName: string }) =>
      api.post<UserRecord>('/api/v1/users', body),
    onSuccess: invalidate,
  });
  const assignRole = useMutation({
    mutationFn: ({ userId, role, justification }: { userId: string; role: Role; justification: string }) =>
      api.post<UserRecord>(`/api/v1/users/${userId}/roles`, { role, justification }),
    onSuccess: invalidate,
  });
  const revokeRole = useMutation({
    mutationFn: ({ userId, role, justification }: { userId: string; role: Role; justification: string }) =>
      api.del<UserRecord>(`/api/v1/users/${userId}/roles`, { role, justification }),
    onSuccess: invalidate,
  });
  const deactivate = useMutation({
    mutationFn: ({ userId }: { userId: string }) => api.del<UserRecord>(`/api/v1/users/${userId}`),
    onSuccess: invalidate,
  });

  return { create, assignRole, revokeRole, deactivate };
}
