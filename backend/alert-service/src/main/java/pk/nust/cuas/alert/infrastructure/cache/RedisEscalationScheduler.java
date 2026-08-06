package pk.nust.cuas.alert.infrastructure.cache;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import pk.nust.cuas.alert.domain.port.EscalationScheduler;

/**
 * The Redis sorted-set escalation scheduler (ARCHITECTURE.md section 4.1.8).
 *
 * <p>One sorted set, scored by the deadline in epoch milliseconds. "Which alerts are due?" is then
 * {@code ZRANGEBYSCORE -inf now}, an O(log n + m) range query rather than a scan of every open
 * alert — which is the difference that matters once a busy sector has thousands of them.
 *
 * <p><strong>Every failure here is swallowed, and that is safe only because of the reconciliation
 * in {@code EscalationSweep}.</strong> A failed schedule leaves the deadline on the alert row, where
 * the database sweep finds it; a failed read returns nothing, and the same query covers it. The
 * opposite choice — propagating the failure — would abort the transaction that raised an alert
 * because a cache was unreachable, turning a slower escalation into no alert at all. That inversion
 * is what section 5.6's governing principle forbids, and it is the same reasoning
 * {@code RedisTrackStateStore} applies to the hot-state cache.
 */
public class RedisEscalationScheduler implements EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RedisEscalationScheduler.class);

    /** The single sorted set. Not partitioned by sector: the sweep wants a global "what is due". */
    static final String KEY = "cuas:alert:escalations";

    private final StringRedisTemplate redis;

    public RedisEscalationScheduler(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void schedule(UUID alertId, Instant dueAt) {
        try {
            redis.opsForZSet().add(KEY, alertId.toString(), dueAt.toEpochMilli());
        } catch (DataAccessException ex) {
            log.warn("Could not schedule escalation for {}; the database sweep will recover it",
                    alertId, ex);
        }
    }

    @Override
    public void cancel(UUID alertId) {
        try {
            redis.opsForZSet().remove(KEY, alertId.toString());
        } catch (DataAccessException ex) {
            // Worse than a failed schedule: a stale entry fires an escalation for an alert that was
            // acknowledged. Harmless in practice — escalate() re-reads the alert and finds it no
            // longer escalatable — but logged at warn because it means Redis is unhealthy.
            log.warn("Could not cancel the escalation for {}; a stale entry may fire and be"
                    + " discarded when the alert is re-read", alertId, ex);
        }
    }

    @Override
    public List<UUID> due(Instant deadline, int limit) {
        try {
            Set<String> ids = redis.opsForZSet()
                    .rangeByScore(KEY, Double.NEGATIVE_INFINITY, deadline.toEpochMilli(), 0, limit);
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream().map(RedisEscalationScheduler::parse).filter(java.util.Objects::nonNull)
                    .toList();
        } catch (DataAccessException ex) {
            log.warn("Could not read the escalation schedule; falling back to the database sweep", ex);
            return List.of();
        }
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            log.warn("Discarding an unparseable entry in the escalation schedule: {}", id);
            return null;
        }
    }
}
