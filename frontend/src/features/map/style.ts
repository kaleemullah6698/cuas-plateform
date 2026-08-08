import type { StyleSpecification } from 'maplibre-gl';

/**
 * A blank dark tactical canvas (ARCHITECTURE.md §3.1). No basemap tiles: self-hosted vector tiles for
 * air-gapped deployment are an M11 concern, and a tactical display is legible — arguably more so —
 * against a plain dark ground with only the operational overlays on it. Works fully offline.
 */
export function blankDarkStyle(): StyleSpecification {
  return {
    version: 8,
    sources: {},
    layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#0a0e14' } }],
  };
}

/**
 * The active map style. A deployment can point {@code VITE_MAP_STYLE_URL} at a self-hosted vector
 * style (M11) without any code change; absent that, the offline blank canvas is used.
 */
export function resolveMapStyle(): string | StyleSpecification {
  const url = import.meta.env.VITE_MAP_STYLE_URL;
  return url && url.length > 0 ? url : blankDarkStyle();
}
