import { create } from 'zustand';
import type { Severity } from '@/lib/severity';

/**
 * The track console's filter state (§2.4.2: "Threat ≥ LOW", search). UI state, so it lives in Zustand
 * (add-frontend-feature skill's four-domains rule) and is local to this feature. The threat threshold
 * is a {@link Severity} floor; {@code 'unknown'} shows everything.
 */
interface TrackFilterState {
  minSeverity: Severity;
  query: string;
  setMinSeverity: (severity: Severity) => void;
  setQuery: (query: string) => void;
}

export const useTrackFilters = create<TrackFilterState>((set) => ({
  minSeverity: 'unknown',
  query: '',
  setMinSeverity: (minSeverity) => set({ minSeverity }),
  setQuery: (query) => set({ query }),
}));
