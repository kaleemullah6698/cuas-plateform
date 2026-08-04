package pk.nust.cuas.ingestion.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import pk.nust.cuas.ingestion.domain.model.HealthStatus;

/**
 * Decides a sensor's {@link HealthStatus} from how long ago it last reported (ARCHITECTURE.md
 * section 4.1.4).
 *
 * <p>A sensor that reported within the online window is {@code ONLINE}; one quiet for longer than
 * that but within the degraded window is {@code DEGRADED}; one quiet beyond it, or never seen, is
 * {@code OFFLINE}. Expressing this as a policy object keeps the thresholds in one place and lets the
 * decision be unit-tested without a clock or a scheduler.
 */
public final class HealthStatusPolicy {

    private final Duration onlineWithin;
    private final Duration degradedWithin;

    /**
     * @param onlineWithin   maximum silence still considered healthy
     * @param degradedWithin maximum silence still considered degraded rather than offline
     * @throws IllegalArgumentException when the degraded window is not longer than the online window
     */
    public HealthStatusPolicy(Duration onlineWithin, Duration degradedWithin) {
        if (degradedWithin.compareTo(onlineWithin) <= 0) {
            throw new IllegalArgumentException(
                    "degradedWithin (" + degradedWithin + ") must exceed onlineWithin (" + onlineWithin + ")");
        }
        this.onlineWithin = onlineWithin;
        this.degradedWithin = degradedWithin;
    }

    /**
     * @param lastDetectionAt the platform clock at the last accepted detection, or empty if none
     * @param now             the current instant
     * @return the sensor's judged health
     */
    public HealthStatus evaluate(Optional<Instant> lastDetectionAt, Instant now) {
        if (lastDetectionAt.isEmpty()) {
            return HealthStatus.OFFLINE;
        }
        Duration silence = Duration.between(lastDetectionAt.get(), now);
        if (silence.compareTo(onlineWithin) <= 0) {
            return HealthStatus.ONLINE;
        }
        if (silence.compareTo(degradedWithin) <= 0) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.OFFLINE;
    }
}
