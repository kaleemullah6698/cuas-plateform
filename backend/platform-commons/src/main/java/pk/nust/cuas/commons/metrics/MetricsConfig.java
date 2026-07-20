package pk.nust.cuas.commons.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies the platform's standard metric conventions to a service's {@link MeterRegistry}.
 *
 * <p>Every metric emitted by any service is tagged with the originating {@code application}, so a
 * single Prometheus instance scraping all services can slice by service without ambiguity, and the
 * shared Grafana dashboards can rely on the tag being present everywhere.
 */
@Configuration
@ConditionalOnClass({MeterRegistry.class, MeterRegistryCustomizer.class})
public class MetricsConfig {

    /**
     * Tags every meter with the service's application name.
     *
     * @param applicationName the value of {@code spring.application.name}
     * @return a customizer adding the common {@code application} tag
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:unknown}") String applicationName) {
        return registry -> registry.config().commonTags("application", applicationName);
    }

    /**
     * Caps HTTP client/server URI tag cardinality to protect the metrics backend from an
     * unbounded label explosion when path variables leak into metric tags.
     *
     * @return a meter filter bounding {@code uri}-tagged meter cardinality
     */
    @Bean
    public MeterFilter uriCardinalityLimit() {
        return MeterFilter.maximumAllowableTags("http.server.requests", "uri", 100,
                MeterFilter.deny());
    }
}
