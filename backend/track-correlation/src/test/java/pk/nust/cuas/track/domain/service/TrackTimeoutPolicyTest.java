package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.service.TrackTimeoutPolicy.Action;

class TrackTimeoutPolicyTest {

    private static final Instant SEEN = Instant.parse("2026-08-04T12:00:00Z");
    private final TrackTimeoutPolicy policy =
            new TrackTimeoutPolicy(Duration.ofSeconds(10), Duration.ofSeconds(30));

    private Action at(TrackStatus status, long ageSeconds) {
        return policy.evaluate(status, SEEN, SEEN.plusSeconds(ageSeconds));
    }

    @Test
    void aFreshConfirmedTrackNeedsNoTransition() {
        assertThat(at(TrackStatus.CONFIRMED, 5)).isEqualTo(Action.NONE);
    }

    @Test
    void aConfirmedTrackCoastsPastTheCoastTimeout() {
        assertThat(at(TrackStatus.CONFIRMED, 15)).isEqualTo(Action.COAST);
    }

    @Test
    void anyOpenTrackIsLostPastTheLostTimeout() {
        assertThat(at(TrackStatus.CONFIRMED, 35)).isEqualTo(Action.LOSE);
        assertThat(at(TrackStatus.COASTING, 35)).isEqualTo(Action.LOSE);
        assertThat(at(TrackStatus.TENTATIVE, 35)).isEqualTo(Action.LOSE);
    }

    @Test
    void aTentativeTrackDoesNotCoastItWaitsToBeLost() {
        // Between the timeouts a tentative track holds — there is no confirmed trajectory to coast.
        assertThat(at(TrackStatus.TENTATIVE, 20)).isEqualTo(Action.NONE);
    }

    @Test
    void aCoastingTrackHoldsUntilTheLostTimeout() {
        assertThat(at(TrackStatus.COASTING, 20)).isEqualTo(Action.NONE);
    }

    @Test
    void aLostTrackIsTerminal() {
        assertThat(at(TrackStatus.LOST, 100)).isEqualTo(Action.NONE);
    }

    @Test
    void exposesItsTimeouts() {
        assertThat(policy.coastTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.lostTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void aLostTimeoutNotLongerThanTheCoastTimeoutIsRejected() {
        assertThatThrownBy(() -> new TrackTimeoutPolicy(Duration.ofSeconds(30), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
