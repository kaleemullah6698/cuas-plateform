package pk.nust.cuas.threat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * The closure-rate derivation.
 *
 * <p>Worth its own tests because it is the one piece of arithmetic in this service that is not a
 * weighted sum, and because the obvious alternative — using ground speed directly — is wrong in a
 * way that would never show up as an error, only as a systematically overstated threat for every
 * track passing across an asset rather than at it.
 */
class AssetApproachTest {

    @Test
    void aHeadOnApproachClosesAtGroundSpeed() {
        // A zero closest approach means the track is heading exactly at the asset, so all of its
        // speed is closure. The one case where the derivation must collapse to ground speed exactly.
        AssetApproach headOn = new AssetApproach("SITE-1", "Site 1", 0.0, 60.0, true);

        assertThat(headOn.closureRateMs(14.0)).isCloseTo(14.0, within(1e-9));
    }

    @Test
    void aNearlyHeadOnApproachClosesJustBelowGroundSpeed() {
        // Approaching, never reaching: the rate is asymptotic to ground speed as the closest
        // approach shrinks, so it must stay strictly below it for any non-zero CPA.
        AssetApproach nearlyHeadOn = new AssetApproach("SITE-1", "Site 1", 1.0, 60.0, true);

        assertThat(nearlyHeadOn.closureRateMs(14.0))
                .isLessThan(14.0)
                .isCloseTo(14.0, within(0.05));
    }

    @Test
    void aPassingTrackClosesMoreSlowlyThanItsGroundSpeed() {
        // 14 m/s for 47 s covers 658 m along track; with a 640 m closest approach the present
        // separation is hypot(640, 658) = 917.6 m, so the closure rate is (917.6 - 640) / 47.
        AssetApproach passing = new AssetApproach("SITE-1", "Site 1", 640.0, 47.0, true);

        double rate = passing.closureRateMs(14.0);

        assertThat(rate).isCloseTo(5.907, within(0.01));
        assertThat(rate)
                .as("using ground speed directly would have overstated closure by more than double")
                .isLessThan(14.0);
    }

    @Test
    void aRecedingTrackHasNoClosureRate() {
        AssetApproach receding = new AssetApproach("SITE-1", "Site 1", 900.0, 0.0, false);

        assertThat(receding.closureRateMs(30.0)).isZero();
    }

    @Test
    void aTrackAlreadyAtItsClosestPointHasNoClosureRate() {
        // Zero time to CPA: it is there now, so there is no separation left to lose.
        AssetApproach atCpa = new AssetApproach("SITE-1", "Site 1", 200.0, 0.0, true);

        assertThat(atCpa.closureRateMs(14.0)).isZero();
    }

    @Test
    void aStationaryTrackClosesOnNothing() {
        AssetApproach hovering = new AssetApproach("SITE-1", "Site 1", 300.0, 45.0, true);

        assertThat(hovering.closureRateMs(0.0)).isZero();
    }

    @Test
    void closureNeverExceedsGroundSpeed() {
        // A physical invariant: a track cannot lose separation faster than it moves. Worth pinning,
        // because a sign or ordering slip in the hypotenuse would break exactly this.
        for (double cpa = 0.0; cpa <= 2000.0; cpa += 100.0) {
            for (double time = 1.0; time <= 120.0; time += 10.0) {
                AssetApproach approach = new AssetApproach("A", "A", cpa, time, true);
                assertThat(approach.closureRateMs(20.0))
                        .as("cpa=%s time=%s", cpa, time)
                        .isBetween(0.0, 20.0 + 1e-9);
            }
        }
    }

    @Test
    void aNegativeTimeToCpaIsRejectedRatherThanReinterpreted() {
        // The contract reports a receding track with closing=false, not with a negative time; a
        // consumer that ignored the sign would read a receding track as an imminent one.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AssetApproach("SITE-1", "Site 1", 640.0, -47.0, true))
                .withMessageContaining("closing=false");
    }

    @Test
    void anApproachMustNameTheAssetItIsMeasuredTo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AssetApproach("  ", "Site 1", 640.0, 47.0, true));
    }
}
