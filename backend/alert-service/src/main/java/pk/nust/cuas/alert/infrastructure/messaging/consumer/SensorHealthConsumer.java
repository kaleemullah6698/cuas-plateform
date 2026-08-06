package pk.nust.cuas.alert.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.infrastructure.messaging.AlertTopics;
import pk.nust.cuas.ingestion.v1.SensorHealth;

/**
 * Consumes {@code sensor.health.v1} and filters for degradation (ARCHITECTURE.md section 4.1.8, as
 * amended at M7.A).
 *
 * <p><strong>One topic, filtered — not a topic per status.</strong> Section 4.1.8 named
 * {@code sensor.health.degraded.v1} until the M7.A amendment; no such topic ever existed, and
 * section 5.3.1 produces a single {@code sensor.health.v1} carrying a status field. Splitting
 * liveness across a topic per status would put a sensor's transitions on different partitions of
 * different topics with no ordering between them, so a DEGRADED could be processed after the ONLINE
 * that superseded it. Keyed by {@code sensorId} on one topic, a sensor's health has a single ordered
 * history — which is the property alerting actually needs.
 *
 * <p><strong>Sensor degradation is logged, not alerted on, and that is a deliberate limit.</strong>
 * The alert queue in this service is about <em>airspace</em> — tracks, threats, incidents — and
 * section 2.4.4 gives sensor health its own screen with its own acknowledgement flow and its own
 * {@code sensor:acknowledge_fault} permission (section 7.4). Raising airspace alerts for equipment
 * faults would mix two queues with different audiences, different urgency and different permissions.
 * What this consumer establishes is the coverage context an operator needs when reading a track:
 * section 2.4.4's "COVERAGE IMPACT" line. Wiring that into the console is M9's, and the health
 * stream is consumed here from M7 so the wiring has something to read.
 */
@Component
public class SensorHealthConsumer {

    private static final Logger log = LoggerFactory.getLogger(SensorHealthConsumer.class);

    @KafkaListener(topics = AlertTopics.SENSOR_HEALTH,
            groupId = "${spring.kafka.consumer.group-id:alert-service}")
    public void onSensorHealth(ConsumerRecord<String, byte[]> record) {
        try {
            SensorHealth health = SensorHealth.parseFrom(record.value());
            switch (health.getStatus()) {
                case DEGRADED -> log.warn("Sensor {} is degraded; coverage in its sectors is"
                        + " reduced", health.getSensorId());
                case OFFLINE -> log.warn("Sensor {} is offline; coverage in its sectors is lost",
                        health.getSensorId());
                default -> log.debug("Sensor {} reports {}",
                        health.getSensorId(), health.getStatus());
            }
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping an unparseable sensor health event at offset {}",
                    record.offset(), ex);
        }
    }
}
