package pk.nust.cuas.reporting.infrastructure.messaging;

/** The Kafka topics this service consumes for projection and produces its own events on (§4.1.10, §5.2). */
public final class ReportTopics {

    // ── Consumed (projected into the read model) ──
    public static final String ALERT_RAISED = "alert.raised.v1";
    public static final String ALERT_RESOLVED = "alert.resolved.v1";
    public static final String INCIDENT_CREATED = "incident.created.v1";
    public static final String INCIDENT_CLOSED = "incident.closed.v1";
    public static final String TRACK_CREATED = "track.created.v1";
    public static final String SENSOR_HEALTH = "sensor.health.v1";
    public static final String AI_INFERENCE_COMPLETED = "ai.inference.completed.v1";

    // ── Produced ──
    public static final String REPORT_STARTED = "report.generation.started.v1";
    public static final String REPORT_GENERATED = "report.generated.v1";
    public static final String REPORT_FAILED = "report.generation.failed.v1";

    private ReportTopics() {
    }
}
