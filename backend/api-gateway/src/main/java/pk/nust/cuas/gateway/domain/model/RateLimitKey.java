package pk.nust.cuas.gateway.domain.model;

import java.util.Objects;

/**
 * The bucket a request is counted against: a rule together with the identity it is scoped to.
 *
 * <p>Section 7.8 requires keying by "the most specific applicable identity", which is why the
 * identity is carried here rather than assumed. Two requests share a bucket only when they match
 * the same rule <em>and</em> the same identity, so one user exhausting their quota never affects
 * another.
 *
 * @param rule     the rule being applied
 * @param identity the subject the quota belongs to: a client address, a username, a user id
 */
public record RateLimitKey(RateLimitRule rule, String identity) {

    public RateLimitKey {
        Objects.requireNonNull(rule, "rule");
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("identity must not be null or blank");
        }
    }

    /**
     * Storage key for this bucket.
     *
     * <p>The rule name is a prefix so that buckets of different rules can never collide even when
     * the identity is the same string — a user id appears in both the per-user API rule and the
     * report-generation rule, and those are separate quotas.
     *
     * @return the key under which the bucket state is held
     */
    public String storageKey() {
        return "cuas:ratelimit:" + rule.name() + ":" + identity;
    }
}
