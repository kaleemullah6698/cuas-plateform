package pk.nust.cuas.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.threat.v1.AssessmentConfidence;
import pk.nust.cuas.threat.v1.Recommendation;
import pk.nust.cuas.threat.v1.ThreatAssessment;
import pk.nust.cuas.threat.v1.ThreatFactor;
import pk.nust.cuas.threat.v1.ThreatFactorType;
import pk.nust.cuas.threat.v1.ThreatLevel;
import pk.nust.cuas.threat.v1.ThreatLevelChanged;
import pk.nust.cuas.threat.v1.ThreatRecommendationGenerated;

/**
 * Serialization round-trip coverage for every message in {@code threat.proto}
 * (ARCHITECTURE.md sections 5.3.3 and 6.3.4), plus the two vocabulary guarantees section 6.3.4
 * makes: the factor set and the advisory recommendation set are both closed.
 */
class ThreatSerdeTest {

    @Test
    void assessmentRoundTripsWithTheFullSection242Breakdown() throws Exception {
        // The worked example rendered in section 2.4.2's THREAT SCORE BREAKDOWN panel.
        ThreatAssessment original = ThreatAssessment.newBuilder()
                .setAssessmentId("01J8X4K3N2R5S8")
                .setTrackId("TRK-0473")
                .setSectorId("sector-NE")
                .setCompositeScore(0.853f)
                .setLevel(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .addFactors(factor(ThreatFactorType.THREAT_FACTOR_TYPE_MODEL_CONFIDENCE, 0.87f, 0.40f))
                .addFactors(factor(ThreatFactorType.THREAT_FACTOR_TYPE_PROXIMITY, 0.92f, 0.25f))
                .addFactors(factor(ThreatFactorType.THREAT_FACTOR_TYPE_CLOSING_VELOCITY, 0.78f, 0.20f))
                .addFactors(factor(ThreatFactorType.THREAT_FACTOR_TYPE_TRAJECTORY_INTENT, 0.71f, 0.10f))
                .addFactors(factor(ThreatFactorType.THREAT_FACTOR_TYPE_SENSOR_AGREEMENT, 0.95f, 0.05f))
                .setConfidence(AssessmentConfidence.newBuilder()
                        .setAvailableWeight(1.0f).setFactorsAvailable(5).setFactorsTotal(5)
                        .setIsReliable(true).build())
                .setRecommendation(Recommendation.TRACK_AND_MONITOR)
                .setPolicyId("policy-default").setPolicyVersion("policy-v3")
                .setPolicyEffectiveAt(Timestamp.newBuilder().setSeconds(1_784_000_000).build())
                .setAssessedAt(Timestamp.newBuilder().setSeconds(1_784_562_300).build())
                .setObservedAt(Timestamp.newBuilder().setSeconds(1_784_562_299).build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();

        ThreatAssessment parsed = ThreatAssessment.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getFactorsCount()).isEqualTo(5);
        // The section 6.3.4 weights sum to 1.00, so the composite is in [0,1] by construction.
        assertThat(parsed.getFactorsList()).extracting(ThreatFactor::getWeight)
                .satisfies(weights -> assertThat(weights.stream().mapToDouble(Float::doubleValue).sum())
                        .isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-6)));
        assertThat(parsed.getPolicyVersion()).isEqualTo("policy-v3");
    }

    @Test
    void unavailableFactorCarriesItsReasonAndContributesNothing() throws Exception {
        // Section 6.3.4: a missing factor is reported, never defaulted. A zero would read as
        // "very far" and a one as "on top of the asset"; both are claims with no evidence.
        ThreatAssessment original = ThreatAssessment.newBuilder()
                .setTrackId("TRK-0474")
                .setCompositeScore(0.348f)
                .setLevel(ThreatLevel.THREAT_LEVEL_MEDIUM)
                .addFactors(factor(ThreatFactorType.THREAT_FACTOR_TYPE_MODEL_CONFIDENCE, 0.87f, 0.40f))
                .addFactors(ThreatFactor.newBuilder()
                        .setType(ThreatFactorType.THREAT_FACTOR_TYPE_PROXIMITY)
                        .setAvailable(false)
                        .setUnavailableReason("no protected asset within the prediction horizon")
                        .build())
                .setConfidence(AssessmentConfidence.newBuilder()
                        .setAvailableWeight(0.40f).setFactorsAvailable(1).setFactorsTotal(5)
                        .setIsReliable(false).build())
                .build();

        ThreatAssessment parsed = ThreatAssessment.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        ThreatFactor absent = parsed.getFactors(1);
        assertThat(absent.getAvailable()).isFalse();
        assertThat(absent.getValue()).isZero();
        assertThat(absent.getContribution()).isZero();
        assertThat(absent.getUnavailableReason()).isNotBlank();
        assertThat(parsed.getConfidence().getIsReliable()).isFalse();
    }

    @Test
    void levelChangedRoundTripsAndDistinguishesEscalationFromDeEscalation() throws Exception {
        ThreatLevelChanged up = ThreatLevelChanged.newBuilder()
                .setTrackId("TRK-0473").setSectorId("sector-NE").setAssessmentId("01J8X4K3N2R5S8")
                .setPreviousLevel(ThreatLevel.THREAT_LEVEL_MEDIUM)
                .setCurrentLevel(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .setPreviousScore(0.52f).setCurrentScore(0.853f)
                .setEscalation(true)
                .setRecommendation(Recommendation.ESCALATE_TO_SUPERVISOR)
                .setChangedAt(Timestamp.newBuilder().setSeconds(1_784_562_300).build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();
        ThreatLevelChanged down = up.toBuilder()
                .setPreviousLevel(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .setCurrentLevel(ThreatLevel.THREAT_LEVEL_MEDIUM)
                .setEscalation(false)
                .build();

        assertThat(ThreatLevelChanged.parseFrom(up.toByteArray())).isEqualTo(up);
        assertThat(ThreatLevelChanged.parseFrom(down.toByteArray())).isEqualTo(down);
        assertThat(ThreatLevelChanged.parseFrom(down.toByteArray()).getEscalation()).isFalse();
    }

    @Test
    void recommendationGeneratedRoundTrips() throws Exception {
        ThreatRecommendationGenerated original = ThreatRecommendationGenerated.newBuilder()
                .setRecommendationId("01J8X4K3N2R5S9").setTrackId("TRK-0473")
                .setAssessmentId("01J8X4K3N2R5S8")
                .setRecommendation(Recommendation.REQUEST_VISUAL_ID)
                .setLevel(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .setRationale("closing at 14 m/s, CPA 640 m in 00:47, 3 of 3 modalities agree")
                .setRuleId("rule-critical-closing").setPolicyVersion("policy-v3")
                .setGeneratedAt(Timestamp.newBuilder().setSeconds(1_784_562_301).build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();

        assertThat(ThreatRecommendationGenerated.parseFrom(original.toByteArray()))
                .isEqualTo(original);
    }

    @Test
    void recommendationVocabularyIsExactlySection634() {
        // Section 6.3.4 fixes this vocabulary and CLAUDE.md's scope limit closes it: no value
        // may encode an engagement action. This test fails the build if a value is ever added,
        // which is the point — extending it is an architecture amendment, not a pull request.
        assertThat(Recommendation.values())
                .filteredOn(r -> r != Recommendation.UNRECOGNIZED)
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "RECOMMENDATION_UNSPECIFIED",
                        "MONITOR",
                        "TRACK_AND_MONITOR",
                        "REQUEST_VISUAL_ID",
                        "ESCALATE_TO_SUPERVISOR",
                        "RECOMMEND_OPERATOR_REVIEW");
    }

    @Test
    void factorVocabularyIsExactlySection634() {
        assertThat(ThreatFactorType.values())
                .filteredOn(f -> f != ThreatFactorType.UNRECOGNIZED)
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "THREAT_FACTOR_TYPE_UNSPECIFIED",
                        "THREAT_FACTOR_TYPE_MODEL_CONFIDENCE",
                        "THREAT_FACTOR_TYPE_PROXIMITY",
                        "THREAT_FACTOR_TYPE_CLOSING_VELOCITY",
                        "THREAT_FACTOR_TYPE_TRAJECTORY_INTENT",
                        "THREAT_FACTOR_TYPE_SENSOR_AGREEMENT");
    }

    @Test
    void threatLevelOrdinalsAreOrderedAndUnspecifiedIsNotBelowLow() {
        // The ordinal ordering carries meaning — it is what an "escalation" is measured against.
        assertThat(ThreatLevel.THREAT_LEVEL_LOW.getNumber())
                .isLessThan(ThreatLevel.THREAT_LEVEL_MEDIUM.getNumber());
        assertThat(ThreatLevel.THREAT_LEVEL_MEDIUM.getNumber())
                .isLessThan(ThreatLevel.THREAT_LEVEL_HIGH.getNumber());
        assertThat(ThreatLevel.THREAT_LEVEL_HIGH.getNumber())
                .isLessThan(ThreatLevel.THREAT_LEVEL_CRITICAL.getNumber());
        // UNSPECIFIED means unassessed, so it must remain the proto3 default rather than a
        // sixth level. A track nothing has judged must not read as the safest on the screen.
        assertThat(ThreatLevel.THREAT_LEVEL_UNSPECIFIED.getNumber()).isZero();
        assertThat(ThreatAssessment.getDefaultInstance().getLevel())
                .isEqualTo(ThreatLevel.THREAT_LEVEL_UNSPECIFIED);
    }

    @Test
    void allThreatLevelValuesPreserved() throws Exception {
        for (ThreatLevel level : ThreatLevel.values()) {
            if (level == ThreatLevel.UNRECOGNIZED) {
                continue;
            }
            ThreatAssessment original = ThreatAssessment.newBuilder().setLevel(level).build();
            assertThat(ThreatAssessment.parseFrom(original.toByteArray()).getLevel())
                    .isEqualTo(level);
        }
    }

    private static ThreatFactor factor(ThreatFactorType type, float value, float weight) {
        return ThreatFactor.newBuilder()
                .setType(type).setAvailable(true)
                .setValue(value).setWeight(weight).setContribution(value * weight)
                .build();
    }
}
