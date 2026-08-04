package pk.nust.cuas.ingestion.infrastructure.messaging.producer;

import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;
import pk.nust.cuas.ingestion.application.port.SensorHealthPublisher;
import pk.nust.cuas.ingestion.domain.model.HealthStatus;
import pk.nust.cuas.ingestion.domain.model.SensorHealthSnapshot;
import pk.nust.cuas.ingestion.infrastructure.messaging.IngestionTopics;
import pk.nust.cuas.ingestion.v1.SensorHealth;

/**
 * Kafka adapter publishing per-sensor health to {@code sensor.health.v1}, mapping the domain
 * snapshot to the {@code SensorHealth} contract message (ARCHITECTURE.md sections 4.1.4, 5.3.1).
 *
 * <p>Best-effort: health is a periodic signal, and a single dropped tick corrects itself on the
 * next emission, so it is not worth blocking the emitter or failing the cycle for the other sensors.
 */
@Component
public class SensorHealthKafkaPublisher implements SensorHealthPublisher {

    private final KafkaByteSender sender;

    public SensorHealthKafkaPublisher(KafkaByteSender sender) {
        this.sender = sender;
    }

    @Override
    public void publish(SensorHealthSnapshot snapshot) {
        SensorHealth.Builder health = SensorHealth.newBuilder()
                .setSensorId(snapshot.sensorId())
                .setStatus(toProtoStatus(snapshot.status()));
        snapshot.lastDetectionAt().ifPresent(instant -> health.setLastSeen(Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()));

        sender.sendBestEffort(IngestionTopics.HEALTH, snapshot.sensorId(), health.build().toByteArray());
    }

    private SensorHealth.Status toProtoStatus(HealthStatus status) {
        return switch (status) {
            case ONLINE -> SensorHealth.Status.ONLINE;
            case DEGRADED -> SensorHealth.Status.DEGRADED;
            case OFFLINE -> SensorHealth.Status.OFFLINE;
        };
    }
}
