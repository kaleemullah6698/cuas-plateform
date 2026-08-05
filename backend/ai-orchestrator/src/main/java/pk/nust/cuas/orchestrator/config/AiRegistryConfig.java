package pk.nust.cuas.orchestrator.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;
import pk.nust.cuas.orchestrator.domain.service.AiServiceRegistry;
import pk.nust.cuas.orchestrator.domain.service.ModalityRoutingPolicy;

/**
 * Builds the service registry from configuration (ARCHITECTURE.md sections 6.1, 6.4).
 *
 * <p>The one place configuration becomes domain. The framework-free domain services are constructed
 * here because they carry no Spring annotations — that is what lets the domain compile without Spring
 * (CLAUDE.md section 2).
 *
 * <p>Configuration is validated <b>at startup</b>: an unknown modality, an unknown mode, a gRPC
 * service with no endpoint, or a duplicate id all fail the context rather than the first inference.
 * A misconfigured AI service that only reveals itself under load is precisely the failure this system
 * cannot afford, and a config error at boot is cheap by comparison.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiRegistryConfig {

    private static final Logger log = LoggerFactory.getLogger(AiRegistryConfig.class);

    @Bean
    public AiServiceRegistry aiServiceRegistry(AiProperties properties) {
        List<AiServiceDescriptor> services = properties.services().entrySet().stream()
                .map(entry -> toDescriptor(entry.getKey(), entry.getValue()))
                .toList();
        AiServiceRegistry registry = new AiServiceRegistry(services);

        if (registry.isEmpty()) {
            log.warn("No AI services are registered; inference requests will route nowhere");
        } else {
            log.info("Registered {} AI service(s): {}", registry.all().size(),
                    registry.all().stream()
                            .map(s -> s.id() + "[" + s.mode() + "," + s.modalities() + "]")
                            .collect(Collectors.joining(", ")));
        }
        return registry;
    }

    @Bean
    public ModalityRoutingPolicy modalityRoutingPolicy(AiServiceRegistry registry) {
        return new ModalityRoutingPolicy(registry);
    }

    @Bean
    public pk.nust.cuas.orchestrator.domain.service.CanaryPolicy canaryPolicy() {
        return new pk.nust.cuas.orchestrator.domain.service.CanaryPolicy();
    }

    private static AiServiceDescriptor toDescriptor(String id, AiProperties.Service service) {
        return new AiServiceDescriptor(
                new ServiceId(id),
                parseModalities(id, service.modalities()),
                parseMode(id, service.mode()),
                blankToEmpty(service.endpoint()),
                service.timeout(),
                blankToEmpty(service.modelVersion()),
                service.maxConcurrent(),
                blankToEmpty(service.canaryVersion()),
                service.canaryPercentage());
    }

    private static Set<Modality> parseModalities(String id, List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI service '" + id + "' declares no modalities, so nothing would ever route to it");
        }
        return configured.stream()
                .map(value -> parseModality(id, value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Modality parseModality(String id, String value) {
        try {
            return Modality.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("AI service '" + id + "' declares unknown modality '"
                    + value + "'; expected one of " + Arrays.toString(Modality.values()), ex);
        }
    }

    private static ProviderMode parseMode(String id, String value) {
        try {
            return ProviderMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("AI service '" + id + "' declares unknown mode '"
                    + value + "'; expected mock or grpc", ex);
        }
    }

    private static Optional<String> blankToEmpty(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
