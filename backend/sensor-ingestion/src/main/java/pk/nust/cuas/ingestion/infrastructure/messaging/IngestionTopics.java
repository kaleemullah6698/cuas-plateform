package pk.nust.cuas.ingestion.infrastructure.messaging;

/**
 * The Kafka topics this service produces to and consumes from (ARCHITECTURE.md section 5.2).
 *
 * <p>Held in one place so a topic name is written once. {@code sensor.detection.raw.v1} is both
 * produced (by the intake adapters) and consumed (by the normalizer): see the section 4.1.4 note on
 * the raw topic's dual role.
 */
public final class IngestionTopics {

    public static final String RAW = "sensor.detection.raw.v1";
    public static final String NORMALIZED = "sensor.detection.normalized.v1";
    public static final String HEALTH = "sensor.health.v1";

    private IngestionTopics() {
    }
}
