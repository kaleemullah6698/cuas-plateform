package pk.nust.cuas.threat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The platform's hard scope limit, enforced by the build.
 *
 * <p>This platform produces <strong>advisory recommendations only</strong>. Every recommendation
 * terminates at an operator authorisation gate; RF jamming, GNSS spoofing and cyber takeover are
 * outside the software boundary. ARCHITECTURE.md section 6.3.4 fixes the vocabulary and CLAUDE.md's
 * scope guard closes it, and this test is what makes that a property of the build rather than of the
 * documents alone.
 *
 * <p>It fails the moment a sixth value appears. That is the point: extending the vocabulary is an
 * architecture amendment with supervisor approval, and a failing build is the thing that will
 * actually stop a well-meaning edit that never read the amendment procedure.
 *
 * <p>Its sibling in the contracts module, {@code AlertSerdeTest}, makes the same assertion about the
 * wire format and about {@code EngagementAction}. Both are needed: this one guards what the service
 * can decide, that one guards what it can say.
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
    void noValueNamesAnEngagementAction() {
        // A blunt substring check rather than a semantic one, and deliberately so: the failure this
        // guards against is somebody adding a plausible-sounding value under deadline pressure, and
        // the words below are the ones such a value would be spelled with.
        String[] forbidden = {
                "JAM", "SPOOF", "DISABLE", "DISRUPT", "NEUTRAL", "DEFEAT", "ENGAGE",
                "INTERCEPT", "TAKEOVER", "TAKE_OVER", "KINETIC", "FIRE", "DOWN", "DESTROY"};
        for (Recommendation recommendation : Recommendation.values()) {
            assertThat(forbidden)
                    .as("%s must not name an engagement action — the platform's recommendation"
                            + " vocabulary is advisory only (CLAUDE.md scope guard)", recommendation)
                    .noneMatch(word -> recommendation.name().contains(word));
        }
    }

    @Test
    void theTypeItselfIsClosed() {
        // An enum cannot be subclassed or implemented, so there is no way to introduce a sixth value
        // at runtime. Asserted rather than assumed, because a refactoring to a sealed interface or
        // an interface with implementations would silently reopen the vocabulary while keeping every
        // other test in this class passing.
        assertThat(Recommendation.class.isEnum()).isTrue();
        assertThat(Modifier.isFinal(Recommendation.class.getModifiers())
                || Arrays.stream(Recommendation.values()).allMatch(
                        r -> r.getClass() == Recommendation.class))
                .as("Recommendation must have no constant-specific subclasses that could carry"
                        + " behaviour a reviewer would not see on the enum itself")
                .isTrue();
    }
}
