package pk.nust.cuas.gateway.infrastructure.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.gateway.domain.exception.EdgeStateUnavailableException;
import pk.nust.cuas.gateway.domain.service.TokenDenylist;

/**
 * Reads the {@code jti} revocation denylist of ARCHITECTURE.md section 7.3.
 *
 * <p>Read-only by design. The gateway must be able to refuse a revoked token but must never be
 * able to revoke one: revocation follows from a logout, a logout-all, or an administrative action,
 * all of which are auth-service's to record and audit. A gateway that could write here could
 * silently lock a user out with no audit record of having done so.
 *
 * <p>The key namespace is shared with the writer and is part of the contract between the two
 * services, so it is declared as a constant rather than assembled ad hoc.
 */
@Component
public class RedisTokenDenylist implements TokenDenylist {

    /** Key namespace shared with the auth-service writer (section 7.3). */
    public static final String KEY_PREFIX = "cuas:token:revoked:";

    private final StringRedisTemplate redis;

    public RedisTokenDenylist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            // A token with no jti cannot be revoked individually, so it cannot be trusted to be
            // revocable at all. Section 7.3 requires the claim; treat its absence as revoked.
            return true;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + tokenId));
        } catch (RuntimeException ex) {
            throw new EdgeStateUnavailableException("denylist-lookup", ex);
        }
    }
}
