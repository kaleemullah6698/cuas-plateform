import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

/**
 * The AI service registry (ARCHITECTURE.md §2 Screen 11, §6.1, §6.4).
 *
 * Types mirror ai-orchestrator's admin response exactly. The endpoint is gateway-routed
 * (`/api/v1/ai/services`) and gated on {@code ai:configure}, so this screen is live today.
 *
 * <p><strong>The registry is data, not code.</strong> §6.1's guarantee is that adding a model is a
 * configuration entry and never a code change: the orchestrator routes by declared *modality* — a
 * fact about the sensor — and never by service identity. A test fails the build if a §6.3 service or
 * framework name appears anywhere in ai-orchestrator's source. This screen therefore renders whatever
 * the registry declares, with no knowledge of what any of these services model.
 */
export interface AiServiceRegistration {
  id: string;
  modalities: string[];
  /** MOCK today; a trained service arrives as GRPC with an endpoint, and nothing else changes (§6.4). */
  mode: string;
  endpoint: string;
  timeoutMs: number;
  modelVersion: string;
  canaryVersion: string;
  canaryPercentage: number;
  maxConcurrent: number;
  healthy: boolean;
}

const KEY = ['ai', 'services'] as const;

export function useAiServices() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => api.get<AiServiceRegistration[]>('/api/v1/ai/services'),
    // The registry is deployment configuration and changes rarely; refetch is for health, which does
    // move. Cheap enough at this cardinality to keep the view honest without a socket.
    refetchInterval: 15_000,
  });
}
