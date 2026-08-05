package pk.nust.cuas.orchestrator.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.orchestrator.application.port.DegradationListener;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.service.AiServiceRegistry;
import pk.nust.cuas.orchestrator.infrastructure.provider.MockInferenceProvider;
import pk.nust.cuas.orchestrator.infrastructure.resilience.ResilientInferenceDispatcher;

/**
 * Wires the per-service timeout, circuit breaker and bulkhead (ARCHITECTURE.md sections 4.1.6, 5.6).
 *
 * <p>The breaker parameters are section 5.6's, verbatim: <b>50% failure over 20 calls opens it for
 * 30 seconds</b>. The bulkhead's permit count is per service, defaulting to the 32 concurrent that
 * section 4.1.6 specifies, and is taken from each service's own descriptor so a CPU-only service and
 * a GPU one can differ.
 *
 * <p>Registries rather than annotations: {@code @CircuitBreaker(name = "...")} needs the name at
 * compile time, and the whole point of section 6.1 is that service names exist only in
 * configuration. Resolving instances by id at call time is what keeps a new model a config entry.
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    /** Section 5.6: 50% failure over 20 calls → open 30 s. */
    private static final float FAILURE_RATE_THRESHOLD = 50.0f;
    private static final int SLIDING_WINDOW_CALLS = 20;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(SLIDING_WINDOW_CALLS)
                .minimumNumberOfCalls(SLIDING_WINDOW_CALLS)
                .waitDurationInOpenState(OPEN_DURATION)
                // Half-open lets a few probes through before trusting the service again, so a
                // still-broken service reopens the breaker instead of taking full traffic.
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
                .ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry(AiServiceRegistry services, MeterRegistry meterRegistry) {
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();

        // A bulkhead per registered service, sized from its own descriptor. Created eagerly so the
        // permit counts are visible in metrics before the first request rather than appearing under
        // load, when nobody is looking at a dashboard for the first time.
        for (AiServiceDescriptor service : services.all()) {
            registry.bulkhead(service.id().value(), BulkheadConfig.custom()
                    .maxConcurrentCalls(service.maxConcurrent())
                    // Zero wait: a saturated service should degrade immediately, not add queueing
                    // latency to a request that is already against a deadline (section 5.6).
                    .maxWaitDuration(Duration.ZERO)
                    .build());
        }

        io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics
                .ofBulkheadRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    /**
     * Threads for enforcing deadlines.
     *
     * <p>Virtual threads: one is parked per in-flight inference, and the orchestrator is IO-bound by
     * design (section 4.1.6). A fixed pool would put a second, invisible concurrency limit behind the
     * bulkheads that are supposed to be the only one.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService inferenceTimeoutExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public ResilientInferenceDispatcher resilientInferenceDispatcher(
            CircuitBreakerRegistry breakers, BulkheadRegistry bulkheads,
            MockInferenceProvider mock, DegradationListener degradationListener,
            ExecutorService inferenceTimeoutExecutor) {
        log.info("Resilience configured: breaker {}% over {} calls, open {}s; bulkhead per service",
                FAILURE_RATE_THRESHOLD, SLIDING_WINDOW_CALLS, OPEN_DURATION.toSeconds());
        return new ResilientInferenceDispatcher(
                breakers, bulkheads, (InferenceProvider) mock, degradationListener,
                inferenceTimeoutExecutor);
    }
}
