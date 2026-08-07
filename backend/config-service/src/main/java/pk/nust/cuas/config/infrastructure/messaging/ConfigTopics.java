package pk.nust.cuas.config.infrastructure.messaging;

/** The Kafka topics this service produces to (ARCHITECTURE.md §4.1.12, §5.2). All compact, keyed by namespace. */
public final class ConfigTopics {

    /** Every change, for a consumer that mirrors the whole configuration. */
    public static final String CONFIG_UPDATED = "platform.config.updated.v1";
    /** Sensor-registry changes, for sensor-ingestion. */
    public static final String CONFIG_SENSOR = "platform.config.sensor.updated.v1";
    /** Threat-policy changes, for threat-assessment. */
    public static final String CONFIG_POLICY = "platform.config.policy.updated.v1";
    /** AI endpoint/model changes, for the orchestrator. */
    public static final String CONFIG_AI = "platform.config.ai.updated.v1";

    private ConfigTopics() {
    }
}
