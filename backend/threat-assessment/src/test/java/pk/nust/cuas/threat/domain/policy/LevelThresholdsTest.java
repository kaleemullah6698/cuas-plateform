package pk.nust.cuas.threat.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pk.nust.cuas.threat.domain.model.ThreatLevel;

/**
 * The level boundaries of ARCHITECTURE.md section 6.3.4, as amended at M7.A.
 *
 * <p>Only one of the three is fixed by the specification. {@code CRITICAL >= 0.80} is section
 * 2.4.2's own value; the MEDIUM and HIGH boundaries are a documented judgement, so the tests below
 * assert the anchor and the <em>structural</em> properties the scale must have — total coverage,
 * strict ordering, no unreachable level — rather than pretending the middle two are derived from
 * something they are not.
 */
class LevelThresholdsTest {

    @Test
    void criticalIsSection242sOwnEightyPercent() {
        // The one boundary the specification always stated. It anchors the scale; the other two were
        // chosen around it and are expected to be retuned per deployment.
        assertThat(LevelThresholds.DEFAULT.critical()).isEqualTo(0.80);
    }

    @Test
    void section242sWorkedCompositeLandsOnCritical() {
        // 0.87x0.40 + 0.92x0.25 + 0.78x0.20 + 0.71x0.10 + 0.95x0.05 = 0.8525, rendered as 0.853
        // and as CRITICAL. The only end-to-end number the specification supplies.
        assertThat(LevelThresholds.DEFAULT.levelFor(0.8525)).isEqualTo(ThreatLevel.CRITICAL);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0.0,   LOW",
            "0.299, LOW",
            "0.30,  MEDIUM",
            "0.549, MEDIUM",
            "0.55,  HIGH",
            "0.799, HIGH",
            "0.80,  CRITICAL",
            "1.0,   CRITICAL",
    })
    void boundariesAreLowerInclusiveAndUpperExclusive(double score, ThreatLevel expected) {
        assertThat(LevelThresholds.DEFAULT.levelFor(score)).isEqualTo(expected);
    }

    @Test
    void everyScoreMapsToExactlyOneLevelAndEveryLevelIsReachable() {
        // Total coverage with no gap, and no band so narrow that a level cannot occur — the two
        // properties that have to hold whatever the boundaries are retuned to.
        java.util.EnumSet<ThreatLevel> seen = java.util.EnumSet.noneOf(ThreatLevel.class);
        for (int i = 0; i <= 1000; i++) {
            ThreatLevel level = LevelThresholds.DEFAULT.levelFor(i / 1000.0);
            assertThat(level).isNotNull();
            seen.add(level);
        }
        assertThat(seen).containsExactlyInAnyOrder(ThreatLevel.values());
    }

    @Test
    void defaultBandsAreOfComparableWidth() {
        // The stated basis for the two boundaries the specification left open: no level so narrow a
        // track passes through it unseen, none so wide it stops discriminating.
        double low = LevelThresholds.DEFAULT.medium();
        double medium = LevelThresholds.DEFAULT.high() - LevelThresholds.DEFAULT.medium();
        double high = LevelThresholds.DEFAULT.critical() - LevelThresholds.DEFAULT.high();
        double critical = 1.0 - LevelThresholds.DEFAULT.critical();
        assertThat(low).isBetween(0.15, 0.35);
        assertThat(medium).isBetween(0.15, 0.35);
        assertThat(high).isBetween(0.15, 0.35);
        assertThat(critical).isBetween(0.15, 0.35);
    }

    @Test
    void thresholdsMustAscend() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LevelThresholds(0.60, 0.55, 0.80))
                .withMessageContaining("ascending");
    }

    @Test
    void thresholdsMustLeaveALowBandAndACriticalBand() {
        // A medium threshold of zero would make LOW unreachable, so no track could ever be reported
        // as the least severe thing on the screen.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LevelThresholds(0.0, 0.5, 0.8));
        // A critical threshold of 1.0 would make CRITICAL reachable only by a perfect score.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LevelThresholds(0.3, 0.5, 1.0));
    }
}
