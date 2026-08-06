package pk.nust.cuas.notification.infrastructure.messaging;

/** The Kafka topics this service consumes from and produces to (ARCHITECTURE.md sections 4.1.9, 5.2). */
public final class NotificationTopics {

    // ── Consumed ──
    public static final String ALERT_RAISED = "alert.raised.v1";
    public static final String ALERT_ESCALATED = "alert.escalated.v1";
    public static final String INCIDENT_CREATED = "incident.created.v1";
    /** One health topic, filtered here for {@code OFFLINE} (the M7.A amendment; section 4.1.9). */
    public static final String SENSOR_HEALTH = "sensor.health.v1";

    // ── Produced ──
    public static final String NOTIFICATION_SENT = "notification.sent.v1";
    public static final String NOTIFICATION_FAILED = "notification.failed.v1";

    private NotificationTopics() {
    }
}
