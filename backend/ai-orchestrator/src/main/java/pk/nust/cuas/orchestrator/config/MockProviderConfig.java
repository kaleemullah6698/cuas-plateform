package pk.nust.cuas.orchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.orchestrator.domain.service.FailureInjector;
import pk.nust.cuas.orchestrator.domain.service.LatencyModel;
import pk.nust.cuas.orchestrator.domain.service.ScenarioLibrary;
import pk.nust.cuas.orchestrator.infrastructure.provider.MockInferenceProvider;

/**
 * Wires the mock inference provider (ARCHITECTURE.md section 6.4).
 *
 * <p><b>Unconditional.</b> There is no {@code @ConditionalOnProperty} guarding this bean, and that is
 * deliberate: the mock is not a development mode that a production profile switches off. It is the
 * fallback an open circuit breaker falls onto (section 6.5), so it must exist in every deployment —
 * including one where every service is {@code mode: grpc}. A profile that could remove it would
 * remove the platform's ability to degrade instead of fail.
 *
 * <p>Which services it actually serves is a separate question, answered per service by
 * {@code ai.services.*.mode} and by the resilience layer's fallback.
 */
@Configuration
@EnableConfigurationProperties(MockProperties.class)
public class MockProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(MockProviderConfig.class);

    @Bean
    public ScenarioLibrary scenarioLibrary() {
        return new ScenarioLibrary();
    }

    @Bean
    public LatencyModel latencyModel(MockProperties properties) {
        return new LatencyModel(properties.latencySigma());
    }

    @Bean
    public FailureInjector failureInjector(MockProperties properties) {
        MockProperties.Failure failure = properties.failure();
        FailureInjector injector = new FailureInjector(
                failure.timeoutRate(), failure.modelErrorRate(), failure.lowConfidenceRate());
        if (injector.isEnabled()) {
            log.warn("Mock failure injection is ENABLED (timeout={}, modelError={}, lowConfidence={})"
                            + " — results are deliberately degraded",
                    failure.timeoutRate(), failure.modelErrorRate(), failure.lowConfidenceRate());
        }
        return injector;
    }

    @Bean
    public MockInferenceProvider mockInferenceProvider(ScenarioLibrary scenarios, LatencyModel latency,
                                                       FailureInjector failures,
                                                       MockProperties properties) {
        return new MockInferenceProvider(scenarios, latency, failures, properties.simulateLatency());
    }
}
