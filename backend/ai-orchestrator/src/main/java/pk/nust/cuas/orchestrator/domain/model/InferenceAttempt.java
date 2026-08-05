package pk.nust.cuas.orchestrator.domain.model;

/**
 * How an inference was ultimately produced (ARCHITECTURE.md sections 5.6, 6.5).
 *
 * <p>Separate from {@link InferenceOutcome}, which says how the <em>model</em> did. This says which
 * <em>path</em> answered, and the two are independent: a call can succeed on the primary provider, or
 * succeed on the mock after the primary's breaker opened. Collapsing them would make a fallback
 * indistinguishable from a real result at exactly the moment an operator most needs to know the
 * difference — which is why section 6.5 insists the degraded state propagates to the UI.
 */
public enum InferenceAttempt {

    /** The configured provider answered. */
    PRIMARY,

    /** The primary failed or was rejected, and the mock stood in. */
    FALLBACK_MOCK;

    /** @return whether this result came from a fallback rather than the configured provider */
    public boolean isDegraded() {
        return this == FALLBACK_MOCK;
    }
}
