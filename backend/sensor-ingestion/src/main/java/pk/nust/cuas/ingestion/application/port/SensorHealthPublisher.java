package pk.nust.cuas.ingestion.application.port;

import pk.nust.cuas.ingestion.domain.model.SensorHealthSnapshot;

/**
 * Publishes per-sensor health to {@code sensor.health.v1} (ARCHITECTURE.md sections 4.1.4, 5.2),
 * consumed by the gateway, alerting, and reporting.
 *
 * <p>An output port owned by the application layer and implemented by the Kafka adapter, which maps
 * the domain snapshot to the {@code SensorHealth} contract message.
 */
public interface SensorHealthPublisher {

    /**
     * @param snapshot the sensor's health at emission time
     */
    void publish(SensorHealthSnapshot snapshot);
}
