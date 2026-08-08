import type { FeatureCollection } from 'geojson';
import maplibregl, { type ExpressionSpecification, type GeoJSONSource } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useRealtime } from '@/app/RealtimeContext';
import { useAlerts, useTracks } from '@/realtime/hooks';
import { useSelectionStore } from '@/store/selectionStore';
import { severityByTrack, toHeadingFeatures, toTrackFeatures } from './geojson';
import { MAP_LAYER_MEMBERS, useMapLayers } from './layers';
import { resolveMapStyle } from './style';
import { TrackTrails } from './trails';

const DEFAULT_CENTER: [number, number] = [73.0, 33.6];
const emptyCollection = (): FeatureCollection => ({ type: 'FeatureCollection', features: [] });

// Colour by severity — the one place the ramp becomes a MapLibre expression (canvas can't take a class).
const SEVERITY_COLOR: ExpressionSpecification = [
  'match',
  ['get', 'severity'],
  'critical', '#f0454e',
  'high', '#f5872b',
  'medium', '#eab308',
  'low', '#2fbf71',
  '#6b7887',
];

/**
 * The tactical map (ARCHITECTURE.md §2.4.2, §3.7). MapLibre is imperative and lives outside React: the
 * map is created once, its sources and layers are installed once, and live data is pushed with
 * {@code source.setData} — never by re-adding layers — throttled to the §2.5.1 10 Hz map budget. React
 * only feeds it the current stores and reflects selection. The M11 layers (zones, sensors, prediction)
 * are installed empty and hidden; M11 fills them without touching this file.
 */
export function MapView() {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const readyRef = useRef(false);
  const centeredRef = useRef(false);
  const trailsRef = useRef(new TrackTrails());
  const [failed, setFailed] = useState(false);

  const realtime = useRealtime();
  const tracks = useTracks(realtime.tracks);
  const alerts = useAlerts(realtime.alerts);
  const selectedTrackId = useSelectionStore((s) => s.selectedTrackId);
  const select = useSelectionStore((s) => s.select);
  const visible = useMapLayers((s) => s.visible);

  // Latest data in a ref so the once-created map's handlers read current values, not a stale closure.
  const dataRef = useRef({ tracks, alerts, selectedTrackId });
  dataRef.current = { tracks, alerts, selectedTrackId };

  const pushData = useCallback(() => {
    const map = mapRef.current;
    if (!map || !readyRef.current) {
      return;
    }
    const current = dataRef.current;
    const severity = severityByTrack(current.alerts);
    trailsRef.current.record(current.tracks);
    const { priority, clusterable } = toTrackFeatures(current.tracks, severity, current.selectedTrackId);
    (map.getSource('tracks-priority') as GeoJSONSource | undefined)?.setData(priority);
    (map.getSource('tracks-clusterable') as GeoJSONSource | undefined)?.setData(clusterable);
    (map.getSource('track-headings') as GeoJSONSource | undefined)?.setData(toHeadingFeatures(current.tracks));
    (map.getSource('track-trails') as GeoJSONSource | undefined)?.setData(trailsRef.current.toFeatures(severity));

    if (!centeredRef.current) {
      const first = current.tracks.find((t) => t.latitude != null && t.longitude != null);
      if (first) {
        map.setCenter([first.longitude as number, first.latitude as number]);
        centeredRef.current = true;
      }
    }
  }, []);

  // ── Create the map once ──
  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    let map: maplibregl.Map;
    try {
      map = new maplibregl.Map({
        container: containerRef.current,
        style: resolveMapStyle(),
        center: DEFAULT_CENTER,
        zoom: 9,
        attributionControl: false,
      });
    } catch {
      // No WebGL (e.g. a headless/test environment) — degrade to a message rather than crash.
      setFailed(true);
      return;
    }
    mapRef.current = map;
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right');
    map.on('load', () => {
      installLayers(map);
      wireInteractions(map, select);
      readyRef.current = true;
      pushData();
    });
    return () => {
      readyRef.current = false;
      map.remove();
      mapRef.current = null;
    };
  }, [pushData, select]);

  // ── Push live data, throttled to ~10 Hz ──
  const lastPushRef = useRef(0);
  const pendingRef = useRef<number | null>(null);
  useEffect(() => {
    const now = performance.now();
    const since = now - lastPushRef.current;
    if (since >= 100) {
      lastPushRef.current = now;
      pushData();
    } else if (pendingRef.current == null) {
      pendingRef.current = window.setTimeout(() => {
        pendingRef.current = null;
        lastPushRef.current = performance.now();
        pushData();
      }, 100 - since);
    }
    return () => {
      if (pendingRef.current != null) {
        window.clearTimeout(pendingRef.current);
        pendingRef.current = null;
      }
    };
  }, [tracks, alerts, selectedTrackId, pushData]);

  // ── Reflect layer visibility toggles ──
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !readyRef.current) {
      return;
    }
    for (const [id, members] of Object.entries(MAP_LAYER_MEMBERS)) {
      const state = visible[id as keyof typeof visible] ? 'visible' : 'none';
      for (const layer of members) {
        if (map.getLayer(layer)) {
          map.setLayoutProperty(layer, 'visibility', state);
        }
      }
    }
  }, [visible]);

  if (failed) {
    return (
      <div className="grid h-full place-items-center bg-background text-sm text-muted-foreground">
        The map could not initialise (WebGL unavailable).
      </div>
    );
  }
  return <div ref={containerRef} className="h-full w-full" />;
}

function installLayers(map: maplibregl.Map): void {
  map.addSource('tracks-clusterable', {
    type: 'geojson',
    data: emptyCollection(),
    cluster: true,
    clusterRadius: 44,
    clusterMaxZoom: 13,
  });
  for (const id of ['tracks-priority', 'track-headings', 'track-trails', 'protected-zones', 'sensors', 'prediction']) {
    map.addSource(id, { type: 'geojson', data: emptyCollection() });
  }

  map.addLayer({
    id: 'trails-line',
    type: 'line',
    source: 'track-trails',
    paint: { 'line-color': SEVERITY_COLOR, 'line-width': 1.5, 'line-opacity': 0.45 },
  });
  map.addLayer({
    id: 'headings-line',
    type: 'line',
    source: 'track-headings',
    paint: { 'line-color': '#93a1b1', 'line-width': 1, 'line-opacity': 0.6 },
  });

  // ── M11 layers: installed empty and hidden; data + visibility arrive in M11 ──
  map.addLayer({ id: 'zones-fill', type: 'fill', source: 'protected-zones', paint: { 'fill-color': '#38bdf8', 'fill-opacity': 0.08 }, layout: { visibility: 'none' } });
  map.addLayer({ id: 'zones-line', type: 'line', source: 'protected-zones', paint: { 'line-color': '#38bdf8', 'line-width': 1, 'line-dasharray': [2, 2] }, layout: { visibility: 'none' } });
  map.addLayer({ id: 'prediction-line', type: 'line', source: 'prediction', paint: { 'line-color': '#93a1b1', 'line-width': 1.2, 'line-dasharray': [1, 2] }, layout: { visibility: 'none' } });
  map.addLayer({ id: 'sensors-point', type: 'circle', source: 'sensors', paint: { 'circle-color': '#38bdf8', 'circle-radius': 5, 'circle-stroke-color': '#0a0e14', 'circle-stroke-width': 1.5 }, layout: { visibility: 'none' } });

  map.addLayer({
    id: 'clusters',
    type: 'circle',
    source: 'tracks-clusterable',
    filter: ['has', 'point_count'],
    paint: {
      'circle-color': '#1f2937',
      'circle-stroke-color': '#3b82f6',
      'circle-stroke-width': 1.5,
      'circle-radius': ['step', ['get', 'point_count'], 12, 10, 16, 50, 22],
    },
  });
  map.addLayer({
    id: 'tracks-clusterable-point',
    type: 'circle',
    source: 'tracks-clusterable',
    filter: ['!', ['has', 'point_count']],
    paint: { 'circle-color': SEVERITY_COLOR, 'circle-radius': 5, 'circle-stroke-color': '#0a0e14', 'circle-stroke-width': 1.5 },
  });
  map.addLayer({
    id: 'tracks-priority-point',
    type: 'circle',
    source: 'tracks-priority',
    paint: { 'circle-color': SEVERITY_COLOR, 'circle-radius': 6.5, 'circle-stroke-color': '#0a0e14', 'circle-stroke-width': 1.5 },
  });

  const selectionPaint = {
    'circle-radius': 12,
    'circle-color': 'rgba(0,0,0,0)',
    'circle-stroke-color': '#22d3ee',
    'circle-stroke-width': 2,
  } as const;
  map.addLayer({ id: 'selection-clusterable', type: 'circle', source: 'tracks-clusterable', filter: ['==', ['get', 'selected'], true], paint: selectionPaint });
  map.addLayer({ id: 'selection-priority', type: 'circle', source: 'tracks-priority', filter: ['==', ['get', 'selected'], true], paint: selectionPaint });
}

function wireInteractions(map: maplibregl.Map, select: (id: string | null) => void): void {
  for (const layer of ['tracks-clusterable-point', 'tracks-priority-point']) {
    map.on('click', layer, (event) => {
      const feature = event.features?.[0];
      if (feature) {
        select(String(feature.properties?.trackId));
      }
    });
    map.on('mouseenter', layer, () => {
      map.getCanvas().style.cursor = 'pointer';
    });
    map.on('mouseleave', layer, () => {
      map.getCanvas().style.cursor = '';
    });
  }
  map.on('click', 'clusters', (event) => {
    const feature = event.features?.[0];
    const geometry = feature?.geometry;
    const clusterId = feature?.properties?.cluster_id;
    const source = map.getSource('tracks-clusterable') as GeoJSONSource | undefined;
    if (clusterId != null && source && geometry?.type === 'Point') {
      void source.getClusterExpansionZoom(clusterId).then((zoom) => {
        map.easeTo({ center: geometry.coordinates as [number, number], zoom });
      });
    }
  });
  map.on('mouseenter', 'clusters', () => {
    map.getCanvas().style.cursor = 'pointer';
  });
  map.on('mouseleave', 'clusters', () => {
    map.getCanvas().style.cursor = '';
  });
}
