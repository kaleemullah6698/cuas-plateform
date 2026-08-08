import { create } from 'zustand';

/**
 * The currently-selected track, shared across features (the map highlights it, track detail shows it)
 * — cross-feature UI state goes through {@code src/store/}, per the frontend architecture (§3.4), so
 * the map and the track console never import each other.
 */
interface SelectionState {
  selectedTrackId: string | null;
  select: (trackId: string | null) => void;
  toggle: (trackId: string) => void;
}

export const useSelectionStore = create<SelectionState>((set, get) => ({
  selectedTrackId: null,
  select: (trackId) => set({ selectedTrackId: trackId }),
  toggle: (trackId) =>
    set({ selectedTrackId: get().selectedTrackId === trackId ? null : trackId }),
}));
