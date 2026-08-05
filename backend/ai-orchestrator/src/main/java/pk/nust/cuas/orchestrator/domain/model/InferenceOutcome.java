package pk.nust.cuas.orchestrator.domain.model;

/**
 * How an inference turned out (ARCHITECTURE.md section 6.2, {@code InferenceStatus}).
 *
 * <p>The domain's own enum, mapped to the wire status at the boundary, so the domain compiles without
 * the generated types (CLAUDE.md section 2).
 *
 * <p>The distinction that matters throughout the orchestrator: <b>{@link #isTransportFailure()}
 * separates a service that could not be reached from a model that ran and had nothing confident to
 * say.</b> Only the former is evidence a circuit breaker should act on. Opening a breaker because a
 * model honestly reported low confidence would take a healthy service out of rotation for telling
 * the truth.
 */
public enum InferenceOutcome {

    /** Ran, and the result is usable. */
    OK(false),

    /** Ran successfully; the result is below the caller's confidence threshold. */
    LOW_CONFIDENCE(false),

    /** Ran, but there was not enough input to infer from. */
    INSUFFICIENT_DATA(false),

    /** The model itself failed — a genuine model error, not a transport one. */
    MODEL_ERROR(false),

    /** The call exceeded its deadline. Transport-level: the service did not answer in time. */
    TIMEOUT(true),

    /** A fallback path produced this — for example the mock standing in for an open breaker. */
    DEGRADED(false);

    private final boolean transportFailure;

    InferenceOutcome(boolean transportFailure) {
        this.transportFailure = transportFailure;
    }

    /**
     * @return whether this outcome is evidence that the <em>service</em> is unhealthy, as opposed to
     *     a valid business answer the model returned
     */
    public boolean isTransportFailure() {
        return transportFailure;
    }

    /** @return whether a caller can use the predictions attached to this outcome */
    public boolean isUsable() {
        return this == OK || this == LOW_CONFIDENCE || this == DEGRADED;
    }
}
