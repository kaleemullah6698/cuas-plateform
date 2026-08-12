import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The configuration API (ARCHITECTURE.md §4.1.12). Types mirror config-service's {@code ConfigDtos}.
 * <b>M11 integration point:</b> config-service is on a bare path, not gateway-routed (G-4), so reads
 * and writes are gated behind {@link CONFIG_REST_ROUTED}. Reads need {@code policy:read}; a write needs
 * the namespace kind's §7.4 permission and a justification, enforced server-side. Built and typed
 * against the real contract; M11 wires the route and flips the flag.
 */
export const CONFIG_REST_ROUTED = true;

export interface ConfigValue {
  namespace: string;
  kind: string;
  version: number;
  value: unknown;
  deleted: boolean;
  lastActorId: string | null;
  lastJustification: string | null;
  updatedAt: string | null;
}

export interface ConfigVersion {
  namespace: string;
  version: number;
  value: unknown;
  deleted: boolean;
  actorId: string | null;
  justification: string | null;
  createdAt: string | null;
}

export function useConfig(namespace: string | null) {
  return useQuery({
    queryKey: ['config', namespace],
    enabled: CONFIG_REST_ROUTED && !!namespace,
    queryFn: () => api.get<ConfigValue>(`/api/v1/config/${encodeURIComponent(namespace as string)}`),
  });
}

export function useConfigHistory(namespace: string | null) {
  return useQuery({
    queryKey: ['config-history', namespace],
    enabled: CONFIG_REST_ROUTED && !!namespace,
    queryFn: () =>
      api.get<ConfigVersion[]>(`/api/v1/config/${encodeURIComponent(namespace as string)}/history`),
  });
}

export function useConfigActions() {
  const client = useQueryClient();
  const invalidate = (namespace: string) => {
    void client.invalidateQueries({ queryKey: ['config', namespace] });
    void client.invalidateQueries({ queryKey: ['config-history', namespace] });
  };

  const rollback = useMutation({
    mutationFn: ({ namespace, toVersion, justification }: { namespace: string; toVersion: number; justification: string }) =>
      api.post<ConfigValue>(`/api/v1/config/${encodeURIComponent(namespace)}/rollback`, { toVersion, justification }),
    onSuccess: (_data, vars) => invalidate(vars.namespace),
  });

  return { rollback };
}
