package pk.nust.cuas.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.alert.v1.AlertEscalated;
import pk.nust.cuas.alert.v1.Incident;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.notification.application.NotificationDispatchService;
import pk.nust.cuas.notification.domain.model.TriggerEvent;

/**
 * Consumes the four source topics a notification can be raised from and dispatches each to the
 * recipients who should hear it (ARCHITECTURE.md section 4.1.9).
 *
 * <p><b>A malformed message is acknowledged, not retried.</b> A record that will not parse now will
 * not parse on redelivery, and blocking the partition on one poison record would silence every alert
 * behind it — the failure section 5.6 exists to prevent. Routing such records to the provisioned DLQ
 * is the M8 hardening item recorded for the pipeline's other consumers. Each dispatch runs under the
 * source event's correlation id so a delivery traces back to the alert that triggered it.
 */
@Component
public class SourceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SourceEventConsumer.class);

    private final NotificationDispatchService dispatch;
    private final SourceEventMapper mapper;

    public SourceEventConsumer(NotificationDispatchService dispatch, SourceEventMapper mapper) {
        this.dispatch = dispatch;
        this.mapper = mapper;
    }

    @KafkaListener(topics = NotificationTopics.ALERT_RAISED,
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onAlertRaised(ConsumerRecord<String, byte[]> record) {
        dispatch(record, Alert::parseFrom, mapper::fromAlertRaised, "alert.raised");
    }

    @KafkaListener(topics = NotificationTopics.ALERT_ESCALATED,
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onAlertEscalated(ConsumerRecord<String, byte[]> record) {
        dispatch(record, AlertEscalated::parseFrom, mapper::fromAlertEscalated, "alert.escalated");
    }

    @KafkaListener(topics = NotificationTopics.INCIDENT_CREATED,
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onIncidentCreated(ConsumerRecord<String, byte[]> record) {
        dispatch(record, Incident::parseFrom, mapper::fromIncidentCreated, "incident.created");
    }

    @KafkaListener(topics = NotificationTopics.SENSOR_HEALTH,
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onSensorHealth(ConsumerRecord<String, byte[]> record) {
        try {
            SensorHealth health = SensorHealth.parseFrom(record.value());
            if (health.getStatus() != SensorHealth.Status.OFFLINE) {
                return;  // only an offline sensor is a notification; other transitions are M9 console context
            }
            runDispatch(mapper.fromSensorOffline(health));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping an unparseable sensor health event at offset {}", record.offset(), ex);
        }
    }

    private <T> void dispatch(ConsumerRecord<String, byte[]> record, ProtoParser<T> parser,
                              Function<T, TriggerEvent> toEvent, String label) {
        try {
            runDispatch(toEvent.apply(parser.parse(record.value())));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping an unparseable {} event at offset {}", label, record.offset(), ex);
        }
    }

    private void runDispatch(TriggerEvent event) {
        CorrelationId.set(event.correlationId().isBlank() ? CorrelationId.generate() : event.correlationId());
        try {
            dispatch.dispatch(event);
        } finally {
            CorrelationId.clear();
        }
    }

    @FunctionalInterface
    private interface ProtoParser<T> {
        T parse(byte[] bytes) throws InvalidProtocolBufferException;
    }
}
