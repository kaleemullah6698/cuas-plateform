package pk.nust.cuas.gateway.domain.service;

/**
 * Port for the revocation denylist of ARCHITECTURE.md section 7.3.
 *
 * <p>An access token is valid for fifteen minutes and is verified from its signature alone, so
 * without a denylist a logout or an administrative revocation would not take effect until the
 * token expired on its own. The denylist is keyed by {@code jti} and each entry lives only as long
 * as the token it revokes, so the set stays bounded by the number of tokens revoked in any
 * fifteen-minute period.
 *
 * <p>Auth-service writes; the gateway and services read.
 */
public interface TokenDenylist {

    /**
     * @param tokenId the {@code jti} claim of the presented access token
     * @return whether the token has been revoked before its natural expiry
     * @throws EdgeStateUnavailableException when the backing store cannot be reached, so that a
     *         revoked token is never accepted merely because the store was down
     */
    boolean isRevoked(String tokenId);
}
