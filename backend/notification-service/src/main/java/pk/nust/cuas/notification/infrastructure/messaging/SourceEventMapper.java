package pk.nust.cuas.notification.infrastructure.messaging;

import java.util.UUID;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.alert.v1.AlertEscalated;
import pk.nust.cuas.alert.v1.Incident;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.SubjectRef;
import pk.nust.cuas.notification.domain.model.TriggerEvent;
import pk.nust.cuas.notification.domain.model.Urgency;
import pk.nust.cuas.threat.v1.ThreatLevel;

/**
 * Projects the source protobuf events onto the domain's {@link TriggerEvent} (ARCHITECTURE.md section
 * 4.1.9), keeping the generated types at the infrastructure edge and out of the domain and dispatch.
 *
 * <p>The {@code dedupSeed} is the source event's own deduplication identity, so one recipient is
 * notified once per thing that happened: once per alert (its dedup key), once per escalation
 * <em>level</em> (a rising alert is worth re-paging), once per incident, and once per offline episode
 * (the sensor's last-seen time is fixed while it is down and advances only when it recovers, so a new
 * offline after a recovery is a new episode). Urgency is the source severity, except a sensor going
 * offline, which carries none and is treated as a fixed system-urgency signal.
 */
@Component
public class SourceEventMapper {

    public TriggerEvent fromAlertRaised(Alert alert) {
        return new TriggerEvent(NotificationTrigger.ALERT_RAISED, urgencyOf(alert.getSeverity()),
                requireSeed(alert.getDedupKey(), "alert-raised:" + alert.getAlertId()),
                SubjectRef.ofAlert(uuid(alert.getAlertId()), uuidOrNull(alert.getTrackId())),
                subject(alert.getTitle(), "Alert raised"), alert.getCorrelationId());
    }

    public TriggerEvent fromAlertEscalated(AlertEscalated escalated) {
        return new TriggerEvent(NotificationTrigger.ALERT_ESCALATED, urgencyOf(escalated.getSeverity()),
                "alert-escalated:" + escalated.getAlertId() + ":" + escalated.getCurrentLevel(),
                SubjectRef.ofAlert(uuid(escalated.getAlertId()), uuidOrNull(escalated.getTrackId())),
                subject(escalated.getReference().isBlank() ? "Alert escalated"
                        : escalated.getReference() + " escalated to level " + escalated.getCurrentLevel(),
                        "Alert escalated"),
                escalated.getCorrelationId());
    }

    public TriggerEvent fromIncidentCreated(Incident incident) {
        UUID trackId = incident.getTrackIdsCount() > 0 ? uuidOrNull(incident.getTrackIds(0)) : null;
        return new TriggerEvent(NotificationTrigger.INCIDENT_CREATED, urgencyOf(incident.getSeverity()),
                "incident-created:" + incident.getIncidentId(),
                SubjectRef.ofIncident(uuid(incident.getIncidentId()), trackId),
                subject(incident.getTitle(), "Incident declared"), incident.getCorrelationId());
    }

    public TriggerEvent fromSensorOffline(SensorHealth health) {
        long lastSeen = health.hasLastSeen() ? health.getLastSeen().getSeconds() : 0L;
        return new TriggerEvent(NotificationTrigger.SENSOR_OFFLINE, Urgency.HIGH,
                "sensor-offline:" + health.getSensorId() + ":" + lastSeen, SubjectRef.none(),
                "Sensor " + health.getSensorId() + " is offline", "");
    }

    private static Urgency urgencyOf(ThreatLevel level) {
        return switch (level) {
            case THREAT_LEVEL_CRITICAL -> Urgency.CRITICAL;
            case THREAT_LEVEL_HIGH -> Urgency.HIGH;
            case THREAT_LEVEL_MEDIUM -> Urgency.MEDIUM;
            default -> Urgency.LOW;
        };
    }

    private static String subject(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private static String requireSeed(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static UUID uuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
