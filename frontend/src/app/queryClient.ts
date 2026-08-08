import { QueryClient } from '@tanstack/react-query';

/**
 * The single TanStack Query client for server state (ARCHITECTURE.md §3.4 — server state is its domain,
 * distinct from the streaming stores that carry live tracks and alerts). Conservative defaults: one
 * retry, a short stale window, and no refetch-on-focus so an operator's console does not stampede the
 * gateway every time the window regains focus.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 15_000,
      refetchOnWindowFocus: false,
    },
  },
});
