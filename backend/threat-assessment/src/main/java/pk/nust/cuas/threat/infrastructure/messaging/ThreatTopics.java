package pk.nust.cuas.threat.infrastructure.messaging;

/** The Kafka topics this service consumes from and produces to (ARCHITECTURE.md sections 4.1.7, 5.2). */
public final class ThreatTopics {

    // ── Consumed ──
    public static final String TRACK_UPDATED = "track.updated.v1";
    public static final String INFERENCE_COMPLETED = "ai.inference.completed.v1";

    // ── Produced ──
    public static final String THREAT_ASSESSED = "threat.assessed.v1";
    public static final String THREAT_LEVEL_CHANGED = "threat.level.changed.v1";
    public static final String THREAT_RECOMMENDATION_GENERATED = "threat.recommendation.generated.v1";

    private ThreatTopics() {
    }
}
