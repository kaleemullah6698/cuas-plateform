package pk.nust.cuas.orchestrator.infrastructure.messaging;

/** The Kafka topics this service produces to and consumes from (ARCHITECTURE.md section 5.2). */
public final class InferenceTopics {

    public static final String INFERENCE_REQUESTED = "ai.inference.requested.v1";
    public static final String INFERENCE_COMPLETED = "ai.inference.completed.v1";
    public static final String INFERENCE_FAILED = "ai.inference.failed.v1";
    public static final String SERVICE_DEGRADED = "ai.service.degraded.v1";

    public static final String TRACK_CREATED = "track.created.v1";
    public static final String TRACK_UPDATED = "track.updated.v1";

    private InferenceTopics() {
    }
}
