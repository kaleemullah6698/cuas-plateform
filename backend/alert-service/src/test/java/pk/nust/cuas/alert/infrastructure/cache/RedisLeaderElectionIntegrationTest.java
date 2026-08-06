package pk.nust.cuas.alert.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Leader election and the escalation schedule against a real Redis (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Against a real server rather than an embedded fake, because what is under test is the atomicity
 * of {@code SET NX PX} and of the compare-and-renew script — precisely the behaviour a fake
 * approximates. A fake that renewed unconditionally would let both instances below believe they
 * lead, and the test would pass.
 */
@Testcontainers
class RedisLeaderElectionIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // ── Leader election ─────────────────────────────────────────────────────────────────────────

    @Test
    void onlyOneInstanceLeadsAtATime() {
        // Without this, every replica fires the same escalation and the operator is paged once per
        // replica for one unacknowledged alert.
        RedisLeaderElection first = election("instance-a", Duration.ofSeconds(30));
        RedisLeaderElection second = election("instance-b", Duration.ofSeconds(30));

        assertThat(first.isLeader()).isTrue();
        assertThat(second.isLeader()).isFalse();
    }

    @Test
    void theLeaderKeepsLeadingWhileItAsks() {
        RedisLeaderElection leader = election("instance-a", Duration.ofSeconds(30));

        assertThat(leader.isLeader()).isTrue();
        assertThat(leader.isLeader()).isTrue();
        assertThat(leader.isLeader()).isTrue();
    }

    @Test
    void aReleasedLeaseIsAvailableImmediately() {
        // What makes a rolling restart seamless: without the release, the new instance waits out the
        // old lease, and nothing sweeps in the meantime.
        RedisLeaderElection first = election("instance-a", Duration.ofSeconds(30));
        RedisLeaderElection second = election("instance-b", Duration.ofSeconds(30));
        first.isLeader();

        first.release();

        assertThat(second.isLeader()).isTrue();
    }

    @Test
    void anExpiredLeaseIsTakenOverWithNobodyIntervening() throws InterruptedException {
        // A crashed leader stops renewing; the lease lapses and a survivor picks it up. This is what
        // makes leadership recoverable without an operator.
        RedisLeaderElection crashed = election("instance-a", Duration.ofMillis(300));
        RedisLeaderElection survivor = election("instance-b", Duration.ofMillis(300));
        assertThat(crashed.isLeader()).isTrue();
        assertThat(survivor.isLeader()).isFalse();

        Thread.sleep(400);

        assertThat(survivor.isLeader()).isTrue();
    }

    @Test
    void anInstanceCannotReleaseAnotherInstancesLease() {
        // The release is a compare-and-delete script for this reason: a plain DEL would let a
        // shutting-down instance strip leadership from the one that had already taken over.
        RedisLeaderElection holder = election("instance-a", Duration.ofSeconds(30));
        RedisLeaderElection other = election("instance-b", Duration.ofSeconds(30));
        holder.isLeader();

        other.release();

        assertThat(holder.isLeader()).as("the holder still holds it").isTrue();
    }

    @Test
    void anInstanceCannotRenewALeaseItDoesNotHold() {
        // The renew is a script for the same reason: a read-then-write has a window in which the
        // lease expires between the check and the extension.
        RedisLeaderElection holder = election("instance-a", Duration.ofSeconds(30));
        holder.isLeader();

        assertThat(redis.opsForValue().get(RedisLeaderElection.KEY)).isEqualTo("instance-a");
        election("instance-b", Duration.ofSeconds(30)).isLeader();
        assertThat(redis.opsForValue().get(RedisLeaderElection.KEY))
                .as("instance-b must not have overwritten the holder")
                .isEqualTo("instance-a");
    }

    // ── Escalation schedule ─────────────────────────────────────────────────────────────────────

    @Test
    void theScheduleReturnsOnlyWhatIsDue() {
        RedisEscalationScheduler scheduler = new RedisEscalationScheduler(redis);
        UUID due = UUID.randomUUID();
        UUID notYet = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.parse("2026-08-06T14:23:06Z");

        scheduler.schedule(due, now.minusSeconds(1));
        scheduler.schedule(notYet, now.plusSeconds(300));

        assertThat(scheduler.due(now, 100)).containsExactly(due);
    }

    @Test
    void reschedulingReplacesTheDeadlineRatherThanAddingOne() {
        // A sorted set keyed by alert id: an alert can only ever be due once, however many times its
        // deadline moves.
        RedisEscalationScheduler scheduler = new RedisEscalationScheduler(redis);
        UUID alertId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.parse("2026-08-06T14:23:06Z");

        scheduler.schedule(alertId, now.minusSeconds(10));
        scheduler.schedule(alertId, now.minusSeconds(1));

        assertThat(scheduler.due(now, 100)).containsExactly(alertId);
    }

    @Test
    void cancellingRemovesAnAlertFromTheSchedule() {
        RedisEscalationScheduler scheduler = new RedisEscalationScheduler(redis);
        UUID alertId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.parse("2026-08-06T14:23:06Z");
        scheduler.schedule(alertId, now.minusSeconds(1));

        scheduler.cancel(alertId);

        assertThat(scheduler.due(now, 100)).isEmpty();
    }

    @Test
    void theScheduleIsOrderedSoonestFirst() {
        RedisEscalationScheduler scheduler = new RedisEscalationScheduler(redis);
        UUID later = UUID.randomUUID();
        UUID sooner = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.parse("2026-08-06T14:23:06Z");

        scheduler.schedule(later, now.minusSeconds(1));
        scheduler.schedule(sooner, now.minusSeconds(60));

        assertThat(scheduler.due(now, 100)).containsExactly(sooner, later);
    }

    private static RedisLeaderElection election(String instanceId, Duration lease) {
        return new RedisLeaderElection(redis, instanceId, lease);
    }
}
