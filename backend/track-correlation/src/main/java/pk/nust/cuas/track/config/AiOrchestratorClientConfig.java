package pk.nust.cuas.track.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.track.application.port.TrackClassifier;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackClassification;
import pk.nust.cuas.track.infrastructure.client.AiOrchestratorGrpcClient;

/**
 * Wires the circuit-broken call to the AI orchestrator (ARCHITECTURE.md sections 4.1.5, 4.3, 6.5).
 *
 * <p>Breaker parameters are section 5.6's, the same as the orchestrator's own: 50% failure over 20
 * calls opens it for 30 seconds. This breaker guards a different failure though — the orchestrator
 * already falls back to its mock internally, so this one opening means the *orchestrator* is
 * unreachable, not that a model is misbehaving.
 *
 * <p>Classification can be switched off entirely. That is not a debugging convenience: until an
 * orchestrator is deployed, a track-correlation instance pointed at nothing would spend a deadline
 * per detection discovering it. The disabled classifier makes "no AI boundary" an explicit,
 * zero-cost configuration rather than a slow failure.
 */
@Configuration
public class AiOrchestratorClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestratorClientConfig.class);

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "correlation.ai.enabled", havingValue = "true", matchIfMissing = true)
    public ManagedChannel aiOrchestratorChannel(
            @org.springframework.beans.factory.annotation.Value("${correlation.ai.endpoint:localhost:9085}")
            String endpoint) {
        log.info("AI orchestrator channel to {}", endpoint);
        return ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
    }

    @Bean
    @ConditionalOnProperty(name = "correlation.ai.enabled", havingValue = "true", matchIfMissing = true)
    public TrackClassifier trackClassifier(
            ManagedChannel aiOrchestratorChannel, Uuidv7Generator idGenerator,
            @org.springframework.beans.factory.annotation.Value("${correlation.ai.timeout:PT0.5S}")
            Duration timeout) {

        CircuitBreaker breaker = CircuitBreaker.of("ai-orchestrator", CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(20)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build());

        return new AiOrchestratorGrpcClient(aiOrchestratorChannel, breaker, idGenerator, timeout);
    }

    /**
     * The classifier for a deployment with no AI boundary: every track stays unclassified.
     *
     * <p>Not a test double. Section 5.6's principle is that a track is never dropped, only degraded,
     * and a deployment running without an orchestrator is exactly that — a correlator that tracks
     * perfectly well and simply does not label anything.
     */
    @Bean
    @ConditionalOnProperty(name = "correlation.ai.enabled", havingValue = "false")
    public TrackClassifier disabledTrackClassifier() {
        log.info("AI classification disabled; tracks will not be classified");
        return (Track track, pk.nust.cuas.detection.v1.DetectionRecord detection)
                -> Optional.<TrackClassification>empty();
    }
}
