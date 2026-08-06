package pk.nust.cuas.alert.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The closed advisory vocabulary of ARCHITECTURE.md section 6.3.4, guarded in this service too.
 *
 * <p>This service does not produce recommendations — it relays what threat-assessment advised — but
 * it declares its own copy of the enum because {@code domain/} compiles without Protobuf. Two copies
 * of a closed vocabulary that could drift would be worse than one, so the test below asserts that
 * this enum matches the generated contract's exactly, not merely that it has five values.
 */
class RecommendationVocabularyTest {

    @Test
    void theVocabularyIsExactlySection634() {
        assertThat(Recommendation.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "MONITOR",
                        "TRACK_AND_MONITOR",
                        "REQUEST_VISUAL_ID",
                        "ESCALATE_TO_SUPERVISOR",
                        "RECOMMEND_OPERATOR_REVIEW");
    }

    @Test
    void theDomainEnumMatchesTheWireContractExactly() {
        // Not "has the same count" — the same names. A domain value the contract cannot express
        // would fail at the mapper, in production, on whichever alert first carried it.
        String[] contractValues = Arrays.stream(pk.nust.cuas.threat.v1.Recommendation.values())
                .filter(r -> r != pk.nust.cuas.threat.v1.Recommendation.UNRECOGNIZED)
                .filter(r -> r != pk.nust.cuas.threat.v1.Recommendation.RECOMMENDATION_UNSPECIFIED)
                .map(Enum::name)
                .toArray(String[]::new);

        assertThat(Recommendation.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(contractValues);
    }

    @Test
    void noValueNamesAnEngagementAction() {
        String[] forbidden = {
                "JAM", "SPOOF", "DISABLE", "DISRUPT", "NEUTRAL", "DEFEAT", "ENGAGE",
                "INTERCEPT", "TAKEOVER", "TAKE_OVER", "KINETIC", "FIRE", "DOWN", "DESTROY"};
        for (Recommendation recommendation : Recommendation.values()) {
            assertThat(forbidden)
                    .as("%s must not name an engagement action", recommendation)
                    .noneMatch(word -> recommendation.name().contains(word));
        }
    }
}
