package pk.nust.cuas.reporting.domain.model;

/**
 * The projected event-type vocabulary (ARCHITECTURE.md §4.1.10): the stable names the consumers write
 * into the read model and the generators aggregate over, deliberately shorter and more stable than the
 * versioned source topic names so a topic bump does not invalidate a report.
 */
public final class ProjectedEventTypes {

    public static final String ALERT_RAISED = "alert.raised";
    public static final String ALERT_RESOLVED = "alert.resolved";
    public static final String INCIDENT_CREATED = "incident.created";
    public static final String INCIDENT_CLOSED = "incident.closed";
    public static final String TRACK_CREATED = "track.created";
    public static final String SENSOR_HEALTH = "sensor.health";
    public static final String AI_INFERENCE = "ai.inference";

    private ProjectedEventTypes() {
    }
}
