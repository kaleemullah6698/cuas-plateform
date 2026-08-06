package pk.nust.cuas.alert.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The platform's hard scope limit, enforced by the build.
 *
 * <p>This platform produces <strong>advisory recommendations only</strong>. RF jamming, GNSS
 * spoofing and cyber takeover are outside the software boundary, and every recommendation terminates
 * at an operator authorisation gate. Section 2.4.3's decision chain renders the fact as a literal
 * line; modelling it as a one-value enum is what turns that rendering into a guarantee.
 *
 * <p>Three layers assert the same thing, deliberately: this test guards the domain, the contracts
 * module's {@code AlertSerdeTest} guards the wire format, and {@code ck_incident_engagement_action}
 * guards the database. Each is a place somebody could otherwise introduce a value without the others
 * noticing.
 */
class EngagementActionTest {

    @Test
    void thereIsExactlyOneEngagementActionAndItIsNone() {
        assertThat(EngagementAction.values())
                .as("adding a value here is an architecture amendment with supervisor approval,"
                        + " not a pull request")
                .extracting(Enum::name)
                .containsExactly("NONE");
    }

    @Test
    void aDecisionChainAlwaysRecordsNone() {
        // The factory supplies the value rather than taking it as a parameter, so no caller has an
        // opportunity to look for another.
        DecisionChain chain = DecisionChain.of(
                Recommendation.TRACK_AND_MONITOR, "Escalate to incident", "usr-4411", "K.Ullah",
                Instant.parse("2026-08-06T14:24:10Z"), "audit:0x8f3a");

        assertThat(chain.engagementAction()).isEqualTo(EngagementAction.NONE);
    }

    @Test
    void theChainRecordsWhenTheOperatorDivergedFromTheAdvice() {
        // The divergence is the reason the chain is recorded at all: a system whose advice is always
        // followed is one nobody is supervising.
        DecisionChain diverged = DecisionChain.of(
                Recommendation.TRACK_AND_MONITOR, "Escalate to incident", "usr-4411", "K.Ullah",
                Instant.parse("2026-08-06T14:24:10Z"), "audit:0x8f3a");
        DecisionChain followed = DecisionChain.of(
                Recommendation.MONITOR, "MONITOR", "usr-4411", "K.Ullah",
                Instant.parse("2026-08-06T14:24:10Z"), "audit:0x8f3a");

        assertThat(diverged.divergedFromAdvice()).isTrue();
        assertThat(followed.divergedFromAdvice()).isFalse();
    }
}
