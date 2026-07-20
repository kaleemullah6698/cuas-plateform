package pk.nust.cuas.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.audit.KafkaAuditPublisher;
import pk.nust.cuas.commons.correlation.CorrelationIdFilter;

class CommonsAutoConfigurationTest {

    @Test
    void registersCorrelationIdFilterInServletApplication() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonsAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    FilterRegistrationBean<?> registration =
                            context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getFilter()).isInstanceOf(CorrelationIdFilter.class);
                });
    }

    @Test
    void doesNotRegisterServletFilterInNonWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonsAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    @Test
    void registersTheFailClosedAuditPublisherWhereverAKafkaTemplateExists() {
        // Regression guard. Every service that audits injects AuditPublisher, but nothing
        // constructed one: the services compiled, their tests passed on mocks, and each would
        // have failed to start. The bean must come from the library, not from twelve copies.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KafkaAutoConfiguration.class, CommonsAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditPublisher.class);
                    assertThat(context.getBean(AuditPublisher.class))
                            .isInstanceOf(KafkaAuditPublisher.class);
                });
    }

    @Test
    void aServiceMaySubstituteItsOwnAuditPublisher() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KafkaAutoConfiguration.class, CommonsAutoConfiguration.class))
                .withBean(AuditPublisher.class, () -> event -> { })
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditPublisher.class);
                    assertThat(context.getBean(AuditPublisher.class))
                            .isNotInstanceOf(KafkaAuditPublisher.class);
                });
    }

    @Test
    void aModuleWithNoBrokerIsUnaffected() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonsAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditPublisher.class);
                });
    }
}
