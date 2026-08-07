import type { SensorView } from './protocol';

/**
 * The sensors' health outside React (ARCHITECTURE.md §2.5.1). Sensor health is a low-rate, server-
 * aggregated snapshot (1 Hz), so — unlike tracks — it needs no frame coalescing; each change publishes
 * a new immutable snapshot the dashboard, map and sensor views read via {@code useSyncExternalStore}.
 */
export class SensorStore {
  private readonly sensors = new Map<string, SensorView>();
  private readonly listeners = new Set<() => void>();
  private currentSnapshot: readonly SensorView[] = [];

  replaceAll(sensors: SensorView[]): void {
    this.sensors.clear();
    for (const sensor of sensors) {
      this.sensors.set(sensor.sensorId, sensor);
    }
    this.publish();
  }

  applyDelta(changed: SensorView[]): void {
    for (const sensor of changed) {
      this.sensors.set(sensor.sensorId, sensor);
    }
    this.publish();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = (): readonly SensorView[] => this.currentSnapshot;

  private publish(): void {
    this.currentSnapshot = Array.from(this.sensors.values());
    for (const listener of this.listeners) {
      listener();
    }
  }
}
