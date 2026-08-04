package pk.nust.cuas.ingestion.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.ingestion.domain.model.HealthStatus;

class HealthStatusPolicyTest {

    private final HealthStatusPolicy policy =
            new HealthStatusPolicy(Duration.ofSeconds(30), Duration.ofMinutes(2));
    private final Instant now = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void aSensorSeenWithinTheOnlineWindowIsOnline() {
        Optional<Instant> lastSeen = Optional.of(now.minusSeconds(10));
        assertThat(policy.evaluate(lastSeen, now)).isEqualTo(HealthStatus.ONLINE);
    }

    @Test
    void aSensorQuietBeyondOnlineButWithinDegradedIsDegraded() {
        Optional<Instant> lastSeen = Optional.of(now.minusSeconds(90));
        assertThat(policy.evaluate(lastSeen, now)).isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void aSensorQuietBeyondTheDegradedWindowIsOffline() {
        Optional<Instant> lastSeen = Optional.of(now.minusSeconds(600));
        assertThat(policy.evaluate(lastSeen, now)).isEqualTo(HealthStatus.OFFLINE);
    }

    @Test
    void aSensorNeverSeenIsOffline() {
        assertThat(policy.evaluate(Optional.empty(), now)).isEqualTo(HealthStatus.OFFLINE);
    }

    @Test
    void theBoundariesAreInclusive() {
        assertThat(policy.evaluate(Optional.of(now.minusSeconds(30)), now)).isEqualTo(HealthStatus.ONLINE);
        assertThat(policy.evaluate(Optional.of(now.minusSeconds(120)), now)).isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void aDegradedWindowNotLongerThanTheOnlineWindowIsRejected() {
        assertThatThrownBy(() -> new HealthStatusPolicy(Duration.ofMinutes(2), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
