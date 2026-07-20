package pk.nust.cuas.commons.config;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import pk.nust.cuas.commons.correlation.CorrelationIdFilter;
import pk.nust.cuas.commons.metrics.MetricsConfig;

/**
 * Auto-configuration that wires the shared platform concerns into any service on the classpath.
 *
 * <p>A service depends on {@code platform-commons} and receives, without further wiring: a
 * correlation-ID filter on every request (servlet applications only), and the standard metric
 * conventions ({@link MetricsConfig}, active only when Micrometer is present — the import target
 * carries its own conditions). Guarding by condition lets a non-web or non-metered module depend
 * on the library without side effects.
 *
 * <p>Registered through {@code AutoConfiguration.imports}, so it activates by dependency alone and
 * never needs a {@code @ComponentScan} reaching into the library's packages.
 */
@AutoConfiguration
@Import(MetricsConfig.class)
public class CommonsAutoConfiguration {

    /**
     * Registers the correlation-ID filter with highest precedence in servlet applications.
     *
     * @return the filter registration
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Filter.class)
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("correlationIdFilter");
        return registration;
    }
}
