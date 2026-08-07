package pk.nust.cuas.gateway.realtime.view;

/**
 * The projection of a sensor's health for the operator console (ARCHITECTURE.md §2.5.1, §3.5). The
 * gateway maps {@code sensor.health.v1} to this shape for the {@code sensors} channel.
 *
 * @param sensorId the sensor
 * @param status   ONLINE / DEGRADED / OFFLINE (contract enum name)
 * @param lastSeen the platform clock at the last accepted detection (ISO-8601), or null if never
 */
public record SensorView(String sensorId, String status, String lastSeen) {
}
