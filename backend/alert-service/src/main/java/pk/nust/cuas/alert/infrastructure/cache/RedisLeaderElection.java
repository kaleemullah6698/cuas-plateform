package pk.nust.cuas.alert.infrastructure.cache;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import pk.nust.cuas.alert.domain.port.LeaderElection;

/**
 * Leader election over a single Redis key (ARCHITECTURE.md section 4.1.8).
 *
 * <p>{@code SET key instanceId NX PX ttl} to acquire; a compare-and-renew script to hold. The renew
 * is a script rather than a read-then-write because the two-step version has a window in which the
 * lease expires between the check and the extension, letting an instance renew a lease another has
 * already taken.
 *
 * <p><strong>This is not a distributed lock, and the sweep does not treat it as one.</strong> A
 * single Redis is a single point of failure and a lease over it cannot survive a partition: a leader
 * that is merely slow can have its lease expire, a second instance can acquire it, and both will
 * believe they lead. Redlock would not fix this either — the honest position is that leadership is
 * an optimisation. {@code EscalationSweep} is written to be correct without it, applying every
 * escalation through the aggregate's own state guard so a second attempt changes nothing. What
 * leadership buys is that N replicas do not all do the work, not that the work is done once.
 *
 * <p>Failing to reach Redis means <strong>not</strong> leading. That direction is deliberate: two
 * instances sweeping is recoverable, but an instance that assumed leadership because it could not
 * check would sweep alongside a healthy leader for as long as the outage lasted.
 */
public class RedisLeaderElection implements LeaderElection {

    private static final Logger log = LoggerFactory.getLogger(RedisLeaderElection.class);

    static final String KEY = "cuas:alert:escalation-leader";

    /** Extend the lease only if we still hold it. Atomic, so it cannot steal another's lease. */
    private static final RedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    /** Release only our own lease, for the same reason. */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final String instanceId;
    private final Duration leaseDuration;

    /**
     * @param redis         the connection
     * @param instanceId    this instance's identity; must be unique across replicas
     * @param leaseDuration how long a lease lasts without renewal. Comfortably longer than the sweep
     *                      interval, so an ordinary GC pause does not cost leadership, and short
     *                      enough that a crashed leader is replaced promptly.
     */
    public RedisLeaderElection(StringRedisTemplate redis, String instanceId,
                               Duration leaseDuration) {
        this.redis = redis;
        this.instanceId = instanceId;
        this.leaseDuration = leaseDuration;
    }

    @Override
    public boolean isLeader() {
        try {
            Long renewed = redis.execute(RENEW, List.of(KEY), instanceId,
                    Long.toString(leaseDuration.toMillis()));
            if (renewed != null && renewed == 1L) {
                return true;
            }
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(KEY, instanceId, leaseDuration);
            if (Boolean.TRUE.equals(acquired)) {
                log.info("Instance {} acquired the escalation-sweep lease", instanceId);
                return true;
            }
            return false;
        } catch (DataAccessException ex) {
            // Not leading is the safe answer: two sweepers is recoverable, an unchecked assumption
            // of leadership for the length of an outage is not.
            log.warn("Could not determine escalation leadership; standing down this cycle", ex);
            return false;
        }
    }

    @Override
    public void release() {
        try {
            redis.execute(RELEASE, List.of(KEY), instanceId);
        } catch (DataAccessException ex) {
            log.debug("Could not release the escalation lease; it will expire on its own", ex);
        }
    }
}
