package pk.nust.cuas.alert.infrastructure.messaging.producer;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.common.AlertProtoMapper;
import pk.nust.cuas.alert.domain.model.Acknowledgement;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.Escalation;
import pk.nust.cuas.alert.domain.model.Incident;
import pk.nust.cuas.alert.domain.model.Resolution;
import pk.nust.cuas.alert.domain.port.AlertEventPublisher;
import pk.nust.cuas.alert.infrastructure.messaging.AlertTopics;

/**
 * Kafka adapter publishing this service's seven topics (ARCHITECTURE.md sections 4.1.8, 5.2, 5.3.4).
 *
 * <p>Alert events are keyed by <strong>alert id</strong> and incident events by incident id, so each
 * aggregate's events share a partition and stay ordered. A consumer must never see an
 * acknowledgement before the raise it answers — notification-service would page about an alert it
 * had not yet heard of.
 *
 * <p><strong>Publication is synchronous and failure propagates.</strong> The send blocks on the
 * broker acknowledgement, so a failure rolls back the surrounding transaction and the alert is not
 * persisted, leaving the threat transition to be redelivered and processed again. That is the
 * opposite of the orchestrator's logged-and-dropped sends, and deliberately so: there the caller
 * already holds its answer and the event is telemetry, whereas here the event is how the operator
 * finds out. An alert stored but never announced is a threat nobody is told about, which is the
 * outcome this service exists to prevent.
 */
@Component
public class AlertEventKafkaPublisher implements AlertEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertEventKafkaPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafka;
    private final AlertProtoMapper mapper;

    public AlertEventKafkaPublisher(KafkaTemplate<String, byte[]> kafka, AlertProtoMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void publishRaised(Alert alert) {
        send(AlertTopics.ALERT_RAISED, alert.alertId().toString(),
                mapper.toProto(alert).toByteArray());
        log.debug("Published alert.raised for {}", alert.reference());
    }

    @Override
    public void publishAcknowledged(Alert alert, Acknowledgement acknowledgement) {
        send(AlertTopics.ALERT_ACKNOWLEDGED, alert.alertId().toString(),
                mapper.toProto(alert, acknowledgement).toByteArray());
        log.debug("Published alert.acknowledged for {}", alert.reference());
    }

    @Override
    public void publishEscalated(Alert alert, Escalation escalation) {
        send(AlertTopics.ALERT_ESCALATED, alert.alertId().toString(),
                mapper.toProto(alert, escalation).toByteArray());
        log.debug("Published alert.escalated for {} at level {}",
                alert.reference(), escalation.currentLevel());
    }

    @Override
    public void publishResolved(Alert alert, Resolution resolution) {
        send(AlertTopics.ALERT_RESOLVED, alert.alertId().toString(),
                mapper.toProto(alert, resolution).toByteArray());
        log.debug("Published alert.resolved for {} as {}",
                alert.reference(), resolution.disposition());
    }

    @Override
    public void publishIncidentCreated(Incident incident) {
        sendIncident(AlertTopics.INCIDENT_CREATED, incident);
    }

    @Override
    public void publishIncidentUpdated(Incident incident) {
        sendIncident(AlertTopics.INCIDENT_UPDATED, incident);
    }

    @Override
    public void publishIncidentClosed(Incident incident) {
        sendIncident(AlertTopics.INCIDENT_CLOSED, incident);
    }

    private void sendIncident(String topic, Incident incident) {
        send(topic, incident.incidentId().toString(), mapper.toProto(incident).toByteArray());
        log.debug("Published {} for {}", topic, incident.reference());
    }

    private void send(String topic, String key, byte[] payload) {
        try {
            kafka.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing to " + topic, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not publish to " + topic, ex);
        }
    }
}
