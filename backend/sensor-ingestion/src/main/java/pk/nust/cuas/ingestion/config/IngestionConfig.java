package pk.nust.cuas.ingestion.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pk.nust.cuas.ingestion.domain.policy.HealthStatusPolicy;
import pk.nust.cuas.ingestion.domain.service.ClockDriftEstimator;
import pk.nust.cuas.ingestion.domain.service.DetectionValidator;
import pk.nust.cuas.ingestion.domain.service.Uuidv7Generator;

/**
 * Wires the framework-free domain services as beans and enables the pieces the pipeline needs.
 *
 * <p>The domain classes carry no Spring annotations — that is what lets the domain compile without
 * Spring (CLAUDE.md section 2) — so they are constructed here, parameterised from
 * {@link IngestionProperties}. Scheduling is enabled for the periodic health emitter.
 */
@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
@EnableScheduling
public class IngestionConfig {

    /** UTC, the platform's authoritative clock for ingest ordering (section 5.3). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DetectionValidator detectionValidator() {
        return new DetectionValidator();
    }

    @Bean
    public ClockDriftEstimator clockDriftEstimator(IngestionProperties properties) {
        return new ClockDriftEstimator(properties.drift().alpha());
    }

    @Bean
    public Uuidv7Generator uuidv7Generator() {
        return new Uuidv7Generator(new SecureRandom());
    }

    @Bean
    public HealthStatusPolicy healthStatusPolicy(IngestionProperties properties) {
        return new HealthStatusPolicy(
                properties.health().onlineWithin(), properties.health().degradedWithin());
    }
}
