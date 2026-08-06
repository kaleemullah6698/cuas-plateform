package pk.nust.cuas.alert.infrastructure.messaging;

/** The Kafka topics this service consumes from and produces to (ARCHITECTURE.md sections 4.1.8, 5.2). */
public final class AlertTopics {

    // ── Consumed ──
    public static final String THREAT_LEVEL_CHANGED = "threat.level.changed.v1";
    public static final String TRACK_LOST = "track.lost.v1";
    public static final String TRACK_MERGED = "track.merged.v1";
    /**
     * One health topic, filtered on status. Section 4.1.8 named {@code sensor.health.degraded.v1}
     * until the M7.A amendment; that topic never existed. Splitting liveness across a topic per
     * status would give a sensor's transitions no ordering between them.
     */
    public static final String SENSOR_HEALTH = "sensor.health.v1";

    // ── Produced ──
    public static final String ALERT_RAISED = "alert.raised.v1";
    public static final String ALERT_ACKNOWLEDGED = "alert.acknowledged.v1";
    public static final String ALERT_ESCALATED = "alert.escalated.v1";
    public static final String ALERT_RESOLVED = "alert.resolved.v1";
    public static final String INCIDENT_CREATED = "incident.created.v1";
    public static final String INCIDENT_UPDATED = "incident.updated.v1";
    public static final String INCIDENT_CLOSED = "incident.closed.v1";

    private AlertTopics() {
    }
}
