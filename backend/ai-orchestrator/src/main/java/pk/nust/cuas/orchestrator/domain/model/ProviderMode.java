package pk.nust.cuas.orchestrator.domain.model;

/**
 * How a registered service is reached (ARCHITECTURE.md section 6.4, "provider switching is per-service
 * and runtime-configurable").
 *
 * <p>Two modes, chosen per service in configuration. Moving a service from {@link #MOCK} to
 * {@link #GRPC} is the entire integration step for a newly trained model: an endpoint and a timeout,
 * no redeploy and no Track A code change. Reverting is the same edit backwards, which is what makes
 * a regressing model a one-line rollback rather than an incident.
 *
 * <p>{@code MOCK} is not a development-only mode. The mock provider is a first-class component that
 * remains available permanently for demonstrations, CI, and regression testing after real models
 * ship (section 6.4), and it is also the fallback a breaker opens onto (section 6.5).
 */
public enum ProviderMode {

    /** Served by the in-process mock provider — deterministic, latency-realistic, always badged. */
    MOCK,

    /** Served by a real AI service over gRPC {@code InferenceService} (section 6.2). */
    GRPC
}
