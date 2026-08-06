package pk.nust.cuas.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.alert.v1.AlertEscalated;
import pk.nust.cuas.alert.v1.Incident;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.TriggerEvent;
import pk.nust.cuas.notification.domain.model.Urgency;
import pk.nust.cuas.threat.v1.ThreatLevel;

/** The projection of the source protobuf events onto {@link TriggerEvent}. */
class SourceEventMapperTest {

    private final SourceEventMapper mapper = new SourceEventMapper();

    @Test
    void anAlertMapsToItsDedupKeyUrgencyAndReferences() {
        UUID alertId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Alert alert = Alert.newBuilder()
                .setAlertId(alertId.toString())
                .setDedupKey("track-1:CRITICAL:policy-v3")
                .setSeverity(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .setTrackId(trackId.toString())
                .setTitle("CRITICAL — TRK-0473 closing on SITE-1")
                .setCorrelationId("corr-1")
                .build();

        TriggerEvent event = mapper.fromAlertRaised(alert);

        assertThat(event.trigger()).isEqualTo(NotificationTrigger.ALERT_RAISED);
        assertThat(event.urgency()).isEqualTo(Urgency.CRITICAL);
        assertThat(event.dedupSeed()).isEqualTo("track-1:CRITICAL:policy-v3");
        assertThat(event.refs().alertId()).contains(alertId);
        assertThat(event.refs().trackId()).contains(trackId);
        assertThat(event.subject()).isEqualTo("CRITICAL — TRK-0473 closing on SITE-1");
        assertThat(event.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void anEscalationDeduplicatesPerLevelSoARisingAlertRePages() {
        UUID alertId = UUID.randomUUID();
        AlertEscalated escalated = AlertEscalated.newBuilder()
                .setAlertId(alertId.toString())
                .setReference("ALT-9912")
                .setTrackId(UUID.randomUUID().toString())
                .setCurrentLevel(2)
                .setSeverity(ThreatLevel.THREAT_LEVEL_HIGH)
                .setCorrelationId("corr-2")
                .build();

        TriggerEvent event = mapper.fromAlertEscalated(escalated);

        assertThat(event.trigger()).isEqualTo(NotificationTrigger.ALERT_ESCALATED);
        assertThat(event.urgency()).isEqualTo(Urgency.HIGH);
        assertThat(event.dedupSeed()).isEqualTo("alert-escalated:" + alertId + ":2");
        assertThat(event.subject()).contains("ALT-9912").contains("level 2");
    }

    @Test
    void anIncidentMapsToItsFirstTrackAndIncidentReference() {
        UUID incidentId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Incident incident = Incident.newBuilder()
                .setIncidentId(incidentId.toString())
                .setSeverity(ThreatLevel.THREAT_LEVEL_MEDIUM)
                .addTrackIds(trackId.toString())
                .setTitle("Coordinated approach, NE sector")
                .setCorrelationId("corr-3")
                .build();

        TriggerEvent event = mapper.fromIncidentCreated(incident);

        assertThat(event.trigger()).isEqualTo(NotificationTrigger.INCIDENT_CREATED);
        assertThat(event.urgency()).isEqualTo(Urgency.MEDIUM);
        assertThat(event.dedupSeed()).isEqualTo("incident-created:" + incidentId);
        assertThat(event.refs().incidentId()).contains(incidentId);
        assertThat(event.refs().trackId()).contains(trackId);
    }

    @Test
    void anOfflineSensorMapsToAFixedHighUrgencyWithNoReferences() {
        SensorHealth health = SensorHealth.newBuilder()
                .setSensorId("RF-NE-02")
                .setStatus(SensorHealth.Status.OFFLINE)
                .build();

        TriggerEvent event = mapper.fromSensorOffline(health);

        assertThat(event.trigger()).isEqualTo(NotificationTrigger.SENSOR_OFFLINE);
        assertThat(event.urgency()).isEqualTo(Urgency.HIGH);
        assertThat(event.dedupSeed()).startsWith("sensor-offline:RF-NE-02:");
        assertThat(event.refs().alertId()).isEmpty();
        assertThat(event.subject()).contains("RF-NE-02");
    }
}
