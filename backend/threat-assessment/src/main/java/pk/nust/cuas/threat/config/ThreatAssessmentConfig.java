package pk.nust.cuas.threat.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.threat.domain.service.RecommendationEngine;
import pk.nust.cuas.threat.domain.service.ThreatScorer;

/**
 * Wires the framework-free domain services.
 *
 * <p>{@link ThreatScorer} and {@link RecommendationEngine} carry no Spring annotations: they compile
 * without Spring on the classpath, which is the layering test CLAUDE.md section 2 sets for
 * {@code domain/}. They become beans here, in {@code config/}, where knowing about Spring is the
 * package's job.
 */
@Configuration
public class ThreatAssessmentConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Uuidv7Generator uuidv7Generator() {
        return new Uuidv7Generator(new SecureRandom());
    }

    @Bean
    public RecommendationEngine recommendationEngine() {
        return new RecommendationEngine();
    }

    @Bean
    public ThreatScorer threatScorer(RecommendationEngine recommendations, Uuidv7Generator ids) {
        return new ThreatScorer(recommendations, ids);
    }
}
