import { create } from 'zustand';

/**
 * The map's layer registry (Option A, M10.D). Every layer the console will ever show is declared here,
 * whether or not its data exists yet — the ones marked {@code available: false} are the M11 integration
 * points (protected zones, sensor positions, predicted trajectories). Their MapLibre layers are
 * installed empty and hidden today; M11 supplies the data and flips {@code available}, with no change
 * to {@link MapView}'s structure. That is what makes M11 a data-integration milestone, not a rewrite.
 */
export type MapLayerId = 'tracks' | 'heading' | 'trails' | 'zones' | 'sensors' | 'prediction';

export interface MapLayerDescriptor {
  id: MapLayerId;
  label: string;
  /** Whether an authoritative data source exists today. Disabled (with a note) until M11 if not. */
  available: boolean;
  defaultVisible: boolean;
  note?: string;
}

export const MAP_LAYERS: MapLayerDescriptor[] = [
  { id: 'tracks', label: 'Tracks', available: true, defaultVisible: true },
  { id: 'heading', label: 'Heading vectors', available: true, defaultVisible: true },
  { id: 'trails', label: 'Trails', available: true, defaultVisible: true },
  {
    id: 'zones',
    label: 'Protected zones',
    available: false,
    defaultVisible: false,
    note: 'Awaiting config-service geometry (M11)',
  },
  {
    id: 'sensors',
    label: 'Sensor locations',
    available: false,
    defaultVisible: false,
    note: 'Awaiting sensor positions (M11)',
  },
  {
    id: 'prediction',
    label: 'Predicted paths',
    available: false,
    defaultVisible: false,
    note: 'Awaiting gateway TrackView enrichment (M11)',
  },
];

/** The MapLibre layer ids that make up each logical layer, so visibility toggles one concept at once. */
export const MAP_LAYER_MEMBERS: Record<MapLayerId, string[]> = {
  tracks: [
    'clusters',
    'tracks-clusterable-point',
    'tracks-priority-point',
    'selection-clusterable',
    'selection-priority',
  ],
  heading: ['headings-line'],
  trails: ['trails-line'],
  zones: ['zones-fill', 'zones-line'],
  sensors: ['sensors-point'],
  prediction: ['prediction-line'],
};

interface MapLayerState {
  visible: Record<MapLayerId, boolean>;
  toggle: (id: MapLayerId) => void;
}

const initialVisibility = Object.fromEntries(
  MAP_LAYERS.map((layer) => [layer.id, layer.defaultVisible]),
) as Record<MapLayerId, boolean>;

export const useMapLayers = create<MapLayerState>((set, get) => ({
  visible: initialVisibility,
  toggle: (id) => set({ visible: { ...get().visible, [id]: !get().visible[id] } }),
}));
