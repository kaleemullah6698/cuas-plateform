package pk.nust.cuas.gateway.domain.policy;

import java.util.List;
import java.util.Set;

/**
 * Determines the address a request is rate-limited under (ARCHITECTURE.md section 7.8).
 *
 * <p>{@code X-Forwarded-For} is set by the client as easily as by a proxy. Trusting it blindly
 * would make the per-IP login limit worthless — an attacker would send a fresh forged address with
 * every attempt and never fill a bucket. The header is therefore honoured only when the immediate
 * peer is a configured trusted proxy, and ignored otherwise.
 *
 * <p>Where the header is honoured, the <em>last</em> entry not belonging to a trusted proxy is
 * taken. Reading the first entry, as is common, reads whatever the client chose to prepend; walking
 * back from the right stops at the earliest address a trusted hop actually observed.
 */
public final class ClientAddressPolicy {

    private final Set<String> trustedProxies;

    /**
     * @param trustedProxies addresses of proxies whose forwarding headers may be believed; empty
     *                       means no header is trusted and the peer address is always used
     */
    public ClientAddressPolicy(Set<String> trustedProxies) {
        this.trustedProxies = Set.copyOf(trustedProxies);
    }

    /**
     * @param peerAddress    the address of the immediate TCP peer
     * @param forwardedFor   the raw {@code X-Forwarded-For} header, or {@code null}
     * @return the address to key rate limits on; never {@code null}
     */
    public String clientAddress(String peerAddress, String forwardedFor) {
        String peer = peerAddress == null || peerAddress.isBlank() ? "unknown" : peerAddress;
        if (forwardedFor == null || forwardedFor.isBlank() || !trustedProxies.contains(peer)) {
            return peer;
        }
        List<String> hops = List.of(forwardedFor.split(",")).stream()
                .map(String::trim)
                .filter(hop -> !hop.isEmpty())
                .toList();
        for (int i = hops.size() - 1; i >= 0; i--) {
            if (!trustedProxies.contains(hops.get(i))) {
                return hops.get(i);
            }
        }
        return peer;
    }
}
