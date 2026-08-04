package pk.nust.cuas.track.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.track.domain.service.AssociationPolicy;
import pk.nust.cuas.track.domain.service.SectorPolicy;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;

/**
 * Wires the framework-free domain services as beans, parameterised from {@link CorrelationProperties}.
 *
 * <p>The domain classes carry no Spring annotations — that is what lets the domain compile without
 * Spring (CLAUDE.md section 2) — so they are constructed here.
 */
@Configuration
@EnableConfigurationProperties(CorrelationProperties.class)
public class TrackCorrelationConfig {

    /** UTC, for association and record timestamps. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SectorPolicy sectorPolicy(CorrelationProperties properties) {
        return new SectorPolicy(properties.sector().cellDegrees());
    }

    @Bean
    public AssociationPolicy associationPolicy(CorrelationProperties properties) {
        return new AssociationPolicy(
                properties.association().distanceGateM(), properties.association().timeGate());
    }

    @Bean
    public TrackConfirmationPolicy trackConfirmationPolicy(CorrelationProperties properties) {
        return new TrackConfirmationPolicy(properties.confirmation().threshold());
    }

    @Bean
    public Uuidv7Generator uuidv7Generator() {
        return new Uuidv7Generator(new SecureRandom());
    }
}
