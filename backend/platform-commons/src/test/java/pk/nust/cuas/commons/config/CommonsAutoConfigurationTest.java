package pk.nust.cuas.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
}
