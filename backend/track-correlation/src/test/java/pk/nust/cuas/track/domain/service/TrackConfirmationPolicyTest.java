package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrackConfirmationPolicyTest {

    private final TrackConfirmationPolicy policy = new TrackConfirmationPolicy(3);

    @Test
    void doesNotConfirmBelowTheThreshold() {
        assertThat(policy.shouldConfirm(1)).isFalse();
        assertThat(policy.shouldConfirm(2)).isFalse();
    }

    @Test
    void confirmsAtAndAboveTheThreshold() {
        assertThat(policy.shouldConfirm(3)).isTrue();
        assertThat(policy.shouldConfirm(4)).isTrue();
    }

    @Test
    void aThresholdBelowTwoIsRejectedBecauseTheFirstDetectionCreatesTheTrack() {
        assertThatThrownBy(() -> new TrackConfirmationPolicy(1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
