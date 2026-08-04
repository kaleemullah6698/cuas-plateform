package pk.nust.cuas.track.infrastructure.messaging;

/** The Kafka topics this service consumes from and produces to (ARCHITECTURE.md section 5.2). */
public final class TrackTopics {

    public static final String NORMALIZED_DETECTIONS = "sensor.detection.normalized.v1";
    public static final String TRACK_CREATED = "track.created.v1";
    public static final String TRACK_UPDATED = "track.updated.v1";

    private TrackTopics() {
    }
}
