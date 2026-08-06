package pk.nust.cuas.threat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The invariant that carries section 6.3.4's confidence requirement: a factor has either a measured
 * value or a stated reason it has none, and never both or neither.
 */
class ThreatFactorTest {

    @Test
    void anAvailableFactorContributesValueTimesWeight() {
        ThreatFactor factor = ThreatFactor.available(ThreatFactorType.MODEL_CONFIDENCE, 0.87, 0.40);

        assertThat(factor.contribution()).isCloseTo(0.348, within(1e-9));
        assertThat(factor.isAvailable()).isTrue();
    }

    @Test
    void anUnavailableFactorContributesNothingButKeepsItsWeight() {
        // Keeping the weight is what lets the breakdown show what the score is *missing*, rather
        // than merely that something is missing.
        ThreatFactor factor = ThreatFactor.unavailable(
                ThreatFactorType.PROXIMITY, 0.25, "no asset within the horizon");

        assertThat(factor.contribution()).isZero();
        assertThat(factor.weight()).isEqualTo(0.25);
        assertThat(factor.value()).isEmpty();
    }

    @Test
    void aFactorCannotHaveBothAValueAndAReasonItHasNone() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ThreatFactor(ThreatFactorType.PROXIMITY,
                        Optional.of(0.5), 0.25, Optional.of("unavailable")))
                .withMessageContaining("never both or neither");
    }

    @Test
    void aFactorCannotBeSilentlyAbsent() {
        // An absent value with no reason would render as a blank cell in section 2.4.2's panel,
        // which tells the operator nothing about why.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ThreatFactor(ThreatFactorType.PROXIMITY,
                        Optional.empty(), 0.25, Optional.empty()));
    }

    @Test
    void valuesOutsideTheUnitRangeAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ThreatFactor.available(ThreatFactorType.PROXIMITY, 1.4, 0.25));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ThreatFactor.available(ThreatFactorType.PROXIMITY, -0.1, 0.25));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ThreatFactor.available(ThreatFactorType.PROXIMITY, Double.NaN, 0.25));
    }

    @Test
    void confidenceSummarisesOnlyTheAvailableWeight() {
        List<ThreatFactor> factors = List.of(
                ThreatFactor.available(ThreatFactorType.MODEL_CONFIDENCE, 0.87, 0.40),
                ThreatFactor.unavailable(ThreatFactorType.PROXIMITY, 0.25, "no asset"),
                ThreatFactor.unavailable(ThreatFactorType.CLOSING_VELOCITY, 0.20, "no asset"),
                ThreatFactor.unavailable(ThreatFactorType.TRAJECTORY_INTENT, 0.10, "no asset"),
                ThreatFactor.available(ThreatFactorType.SENSOR_AGREEMENT, 1.0, 0.05));

        AssessmentConfidence confidence = AssessmentConfidence.of(factors, 0.50);

        assertThat(confidence.availableWeight()).isCloseTo(0.45, within(1e-9));
        assertThat(confidence.factorsAvailable()).isEqualTo(2);
        assertThat(confidence.factorsTotal()).isEqualTo(5);
        assertThat(confidence.isReliable())
                .as("0.45 of the model's weight is below the 0.50 floor")
                .isFalse();
        assertThat(confidence.isEmpty()).isFalse();
    }

    @Test
    void aFullyEvaluatedScoreIsReliable() {
        List<ThreatFactor> factors = List.of(
                ThreatFactor.available(ThreatFactorType.MODEL_CONFIDENCE, 0.87, 0.40),
                ThreatFactor.available(ThreatFactorType.PROXIMITY, 0.92, 0.25),
                ThreatFactor.available(ThreatFactorType.CLOSING_VELOCITY, 0.78, 0.20),
                ThreatFactor.available(ThreatFactorType.TRAJECTORY_INTENT, 0.71, 0.10),
                ThreatFactor.available(ThreatFactorType.SENSOR_AGREEMENT, 0.95, 0.05));

        AssessmentConfidence confidence = AssessmentConfidence.of(factors, 0.50);

        assertThat(confidence.availableWeight()).isCloseTo(1.0, within(1e-9));
        assertThat(confidence.isReliable()).isTrue();
        // And the composite is section 2.4.2's rendered 0.853.
        assertThat(factors.stream().mapToDouble(ThreatFactor::contribution).sum())
                .isCloseTo(0.853, within(0.001));
    }

    @Test
    void noEvaluableFactorMeansAnEmptyConfidence() {
        AssessmentConfidence confidence = AssessmentConfidence.of(List.of(
                ThreatFactor.unavailable(ThreatFactorType.MODEL_CONFIDENCE, 0.40, "nothing"),
                ThreatFactor.unavailable(ThreatFactorType.PROXIMITY, 0.25, "nothing")), 0.50);

        assertThat(confidence.isEmpty()).isTrue();
        assertThat(confidence.availableWeight()).isZero();
    }
}
