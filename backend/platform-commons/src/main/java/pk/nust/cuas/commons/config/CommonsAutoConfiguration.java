package pk.nust.cuas.commons.config;

import jakarta.servlet.Filter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.kafka.core.KafkaTemplate;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.audit.KafkaAuditPublisher;
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
@AutoConfiguration(after = org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class)
@Import(MetricsConfig.class)
public class CommonsAutoConfiguration {

    /**
     * Registers the fail-closed audit publisher of section 7.5.
     *
     * <p>Every service that audits declares a dependency on {@link AuditPublisher}, and until this
     * bean existed none of them could start: the implementation was written but never wired, and
     * the gap stayed invisible because every test supplies a mock. Registering it here rather than
     * in each service means one definition and one publish timeout across the platform.
     *
     * <p>Conditional on a {@code KafkaTemplate} being present, so a module that depends on commons
     * without a broker — a pure domain library, a test slice — is unaffected. Conditional on there
     * being no other {@link AuditPublisher} so a service, or a test, can still substitute its own.
     *
     * @param kafkaTemplate  the producer
     * @param publishTimeout how long to block on the broker before failing the audited action,
     *                       as an ISO-8601 duration. Parsed here rather than bound as a
     *                       {@link Duration}, so the bean behaves identically in a full
     *                       application and in a bare context that has no conversion service.
     * @return the publisher
     */
    @Bean
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean(AuditPublisher.class)
    public AuditPublisher auditPublisher(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            @Value("${cuas.audit.publish-timeout:PT5S}") String publishTimeout) {
        return new KafkaAuditPublisher(kafkaTemplate, Duration.parse(publishTimeout));
    }

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
