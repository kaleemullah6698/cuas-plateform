package pk.nust.cuas.alert.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The escalation timer index (ARCHITECTURE.md section 4.1.8, "a Redis sorted-set scheduler").
 *
 * <p><strong>An index, not a source of truth.</strong> Every deadline it holds is also on the alert
 * row. That redundancy is deliberate: a deadline that existed only in a cache would mean an
 * unacknowledged critical alert silently ceasing to escalate the moment that cache restarted, and
 * nothing would report it. The scheduler makes "which alerts are due?" a range query instead of a
 * table scan; the database is what makes the answer correct.
 */
public interface EscalationScheduler {

    /**
     * Schedules, or reschedules, an alert's next escalation.
     *
     * @param alertId the alert
     * @param dueAt   when it escalates
     */
    void schedule(UUID alertId, Instant dueAt);

    /**
     * Removes an alert from the schedule — it was acknowledged, resolved, or reached the top of its
     * ladder.
     *
     * @param alertId the alert
     */
    void cancel(UUID alertId);

    /**
     * @param deadline the instant to compare against
     * @param limit    the maximum number to return in one sweep
     * @return the alerts whose deadline has passed, soonest first
     */
    List<UUID> due(Instant deadline, int limit);
}
