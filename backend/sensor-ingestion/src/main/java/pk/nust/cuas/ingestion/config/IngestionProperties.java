package pk.nust.cuas.ingestion.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable parameters of the ingestion pipeline (ARCHITECTURE.md section 4.1.4), bound from the
 * {@code ingestion.*} configuration namespace with defaults that make the service runnable with no
 * configuration at all.
 *
 * @param dedup  the deduplication window
 * @param drift  the clock-drift estimator
 * @param health health telemetry thresholds and cadence
 * @param batch  REST batch-intake limits
 * @param replay the development-only dataset replay endpoint
 */
@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(
        @DefaultValue Dedup dedup,
        @DefaultValue Drift drift,
        @DefaultValue Health health,
        @DefaultValue Batch batch,
        @DefaultValue Replay replay) {

    /**
     * @param ttl how long a {@code (sensorId, sensorSeq)} pair is remembered as seen. Long enough to
     *            catch a redelivery or an adapter retry, short enough not to reject a legitimate
     *            sequence-number wrap.
     */
    public record Dedup(@DefaultValue("PT10M") Duration ttl) {
    }

    /**
     * @param alpha the EWMA smoothing factor in (0,1]; higher tracks a drifting clock faster
     * @param ttl   how long a sensor's drift estimate is retained without a detection
     */
    public record Drift(@DefaultValue("0.2") double alpha, @DefaultValue("PT24H") Duration ttl) {
    }

    /**
     * @param onlineWithin   maximum silence still reported as {@code ONLINE}
     * @param degradedWithin maximum silence still reported as {@code DEGRADED} rather than {@code OFFLINE}
     * @param emitInterval   how often health is recomputed and published
     */
    public record Health(
            @DefaultValue("PT30S") Duration onlineWithin,
            @DefaultValue("PT2M") Duration degradedWithin,
            @DefaultValue("PT15S") Duration emitInterval) {
    }

    /**
     * @param maxSize the largest batch the REST intake accepts in one request, bounding the work a
     *                single call can enqueue
     */
    public record Batch(@DefaultValue("500") int maxSize) {
    }

    /**
     * @param enabled whether the {@code POST /ingest/replay} development endpoint is active. Off by
     *                default: replay is a development aid for pushing a recorded dataset through the
     *                real pipeline, and must never be reachable in a deployment that faces real
     *                sensors. When off, the endpoint is not registered and returns 404.
     */
    public record Replay(@DefaultValue("false") boolean enabled) {
    }
}
