package pk.nust.cuas.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;
import pk.nust.cuas.orchestrator.domain.service.AiServiceRegistry;

class AiRegistryConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AiRegistryConfig.class);

    @Test
    void aConfiguredServiceBecomesARegistryEntry() {
        runner.withPropertyValues(
                        "ai.services.some-service.modalities=RF,RADAR",
                        "ai.services.some-service.mode=mock",
                        "ai.services.some-service.timeout=PT0.25S",
                        "ai.services.some-service.max-concurrent=8")
                .run(context -> {
                    AiServiceRegistry registry = context.getBean(AiServiceRegistry.class);
                    var service = registry.find(new ServiceId("some-service")).orElseThrow();

                    assertThat(service.modalities()).containsExactlyInAnyOrder(Modality.RF, Modality.RADAR);
                    assertThat(service.mode()).isEqualTo(ProviderMode.MOCK);
                    assertThat(service.timeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(service.maxConcurrent()).isEqualTo(8);
                    assertThat(service.endpoint()).isEmpty();
                });
    }

    @Test
    void aGrpcServiceBindsItsEndpointAndPinnedVersion() {
        // This is the entire integration step for a trained model: two extra lines of YAML.
        runner.withPropertyValues(
                        "ai.services.some-service.modalities=RF",
                        "ai.services.some-service.mode=grpc",
                        "ai.services.some-service.endpoint=some-host:9001",
                        "ai.services.some-service.model-version=1.3.0")
                .run(context -> {
                    var service = context.getBean(AiServiceRegistry.class)
                            .find(new ServiceId("some-service")).orElseThrow();

                    assertThat(service.mode()).isEqualTo(ProviderMode.GRPC);
                    assertThat(service.endpoint()).contains("some-host:9001");
                    assertThat(service.modelVersion()).contains("1.3.0");
                });
    }

    @Test
    void anAdditionalServiceNeedsNothingButConfiguration() {
        // Section 6.1's guarantee, exercised: a model nobody has written code for still registers
        // and still routes.
        runner.withPropertyValues(
                        "ai.services.first.modalities=RF",
                        "ai.services.second.modalities=ACOUSTIC,GNSS")
                .run(context -> {
                    AiServiceRegistry registry = context.getBean(AiServiceRegistry.class);

                    assertThat(registry.all()).hasSize(2);
                    assertThat(registry.servicesFor(Modality.GNSS))
                            .extracting(s -> s.id().value())
                            .containsExactly("second");
                });
    }

    @Test
    void noConfiguredServicesIsAValidEmptyRegistry() {
        runner.run(context ->
                assertThat(context.getBean(AiServiceRegistry.class).isEmpty()).isTrue());
    }

    @Test
    void anUnknownModalityFailsAtStartupRatherThanAtTheFirstInference() {
        runner.withPropertyValues("ai.services.some-service.modalities=TELEPATHY")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("TELEPATHY"));
    }

    @Test
    void anUnknownProviderModeFailsAtStartup() {
        runner.withPropertyValues(
                        "ai.services.some-service.modalities=RF",
                        "ai.services.some-service.mode=telepathy")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("mock or grpc"));
    }

    @Test
    void aGrpcServiceWithoutAnEndpointFailsAtStartup() {
        runner.withPropertyValues(
                        "ai.services.some-service.modalities=RF",
                        "ai.services.some-service.mode=grpc")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("endpoint"));
    }

    @Test
    void aServiceWithNoModalitiesFailsAtStartup() {
        runner.withPropertyValues("ai.services.some-service.mode=mock")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("modalities"));
    }
}
