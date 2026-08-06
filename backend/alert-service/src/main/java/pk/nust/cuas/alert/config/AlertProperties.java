package pk.nust.cuas.alert.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable parameters of alerting (ARCHITECTURE.md section 4.1.8), bound from the {@code alert.*}
 * namespace with defaults that make the service runnable with no configuration.
 *
 * @param minimumSeverity the lowest severity that raises an alert at all
 * @param suppression     how long a recurrence is folded into an open alert, per severity
 * @param escalation      the escalation ladder, per severity
 * @param sweep           the escalation sweep and its leader lease
 * @param evidence        where evidence objects live
 */
@ConfigurationProperties(prefix = "alert")
public record AlertProperties(
        @DefaultValue("MEDIUM") String minimumSeverity,
        @DefaultValue Map<String, Duration> suppression,
        @DefaultValue Map<String, List<Duration>> escalation,
        @DefaultValue Sweep sweep,
        @DefaultValue Evidence evidence) {

    /**
     * @param interval      how often the escalation sweep runs. Comfortably shorter than the
     *                      shortest escalation rung, so a deadline is never missed by a whole
     *                      interval.
     * @param leaseDuration how long the leader lease lasts without renewal. Longer than the interval
     *                      so an ordinary GC pause does not cost leadership, short enough that a
     *                      crashed leader is replaced promptly.
     */
    public record Sweep(
            @DefaultValue("PT15S") Duration interval,
            @DefaultValue("PT45S") Duration leaseDuration) {
    }

    /**
     * @param root the directory the filesystem evidence store resolves objects under. MinIO
     *             replaces this adapter at M8; the port does not change.
     */
    public record Evidence(@DefaultValue("./evidence") String root) {
    }
}
