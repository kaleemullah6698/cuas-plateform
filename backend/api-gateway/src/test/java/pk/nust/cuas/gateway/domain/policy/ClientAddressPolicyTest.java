package pk.nust.cuas.gateway.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Whether {@code X-Forwarded-For} may be believed decides whether the per-IP login limit of
 * section 7.8 means anything at all.
 */
class ClientAddressPolicyTest {

    @Test
    void anUntrustedPeerCannotChooseItsOwnIdentity() {
        var policy = new ClientAddressPolicy(Set.of());

        // Otherwise every login attempt could arrive under a fresh address and no bucket would
        // ever fill.
        assertThat(policy.clientAddress("203.0.113.5", "1.2.3.4")).isEqualTo("203.0.113.5");
    }

    @Test
    void aTrustedProxysHeaderIsHonoured() {
        var policy = new ClientAddressPolicy(Set.of("10.0.0.1"));

        assertThat(policy.clientAddress("10.0.0.1", "203.0.113.5")).isEqualTo("203.0.113.5");
    }

    @Test
    void theLastUntrustedHopIsTakenRatherThanTheFirstEntry() {
        var policy = new ClientAddressPolicy(Set.of("10.0.0.1", "10.0.0.2"));

        // The client wrote "1.2.3.4"; the earliest address a trusted hop actually observed is
        // 203.0.113.5, and that is the one to charge.
        assertThat(policy.clientAddress("10.0.0.1", "1.2.3.4, 203.0.113.5, 10.0.0.2"))
                .isEqualTo("203.0.113.5");
    }

    @Test
    void aHeaderOfNothingButTrustedProxiesFallsBackToThePeer() {
        var policy = new ClientAddressPolicy(Set.of("10.0.0.1"));

        assertThat(policy.clientAddress("10.0.0.1", "10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void absentAndBlankInputsStillYieldAnIdentity() {
        var policy = new ClientAddressPolicy(Set.of("10.0.0.1"));

        assertThat(policy.clientAddress("10.0.0.1", null)).isEqualTo("10.0.0.1");
        assertThat(policy.clientAddress("10.0.0.1", "   ")).isEqualTo("10.0.0.1");
        // A bucket key may never be blank, or every such request would share one bucket silently.
        assertThat(policy.clientAddress(null, null)).isEqualTo("unknown");
    }
}
