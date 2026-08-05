package pk.nust.cuas.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tuning for the mock inference provider (ARCHITECTURE.md section 6.4).
 *
 * <p>Defaults describe a healthy system: realistic latency, no injected failure. Injection is opt-in
 * because it exists to *force* a state on demand — for a demonstration of the degraded UI, a
 * resilience test, or a soak run — not to make ordinary development flaky.
 *
 * @param simulateLatency whether to actually sleep for the sampled time. The sampled figure is
 *                        reported either way; sleeping is what makes a timeout and a circuit breaker
 *                        genuinely fire, so it stays on outside unit tests.
 * @param latencySigma    lognormal shape; larger gives a heavier tail. 0.35 puts the median near 60%
 *                        of the p95 budget, which is about how a served model behaves.
 * @param failure         injected failure rates
 */
@ConfigurationProperties(prefix = "ai.mock")
public record MockProperties(
        @DefaultValue("true") boolean simulateLatency,
        @DefaultValue("0.35") double latencySigma,
        @DefaultValue Failure failure) {

    /**
     * @param timeoutRate       fraction of calls that time out — the transport failure a breaker counts
     * @param modelErrorRate    fraction that report a model error
     * @param lowConfidenceRate fraction that succeed but report low confidence
     */
    public record Failure(
            @DefaultValue("0.0") double timeoutRate,
            @DefaultValue("0.0") double modelErrorRate,
            @DefaultValue("0.0") double lowConfidenceRate) {
    }
}
