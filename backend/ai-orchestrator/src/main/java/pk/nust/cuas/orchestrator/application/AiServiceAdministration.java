package pk.nust.cuas.orchestrator.application;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.orchestrator.api.dto.AiServiceResponse;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;
import pk.nust.cuas.orchestrator.domain.service.AiServiceRegistry;

/**
 * Reads and changes the AI service registry for the admin API (ARCHITECTURE.md sections 4.1.6, 6.6).
 *
 * <p>Health is probed through the provider bound to each service's mode, so the answer is about the
 * service as actually configured — a mock-mode service reports the mock's health, which is always
 * serving, and that is correct rather than misleading: what is being asked is "can this service
 * answer", and a mock-backed one can.
 */
@Service
public class AiServiceAdministration {

    private static final Logger log = LoggerFactory.getLogger(AiServiceAdministration.class);

    private final AiServiceRegistry registry;
    private final ProviderSelector providers;

    public AiServiceAdministration(AiServiceRegistry registry, ProviderSelector providers) {
        this.registry = registry;
        this.providers = providers;
    }

    /** @return every registered service with its live health */
    public List<AiServiceResponse> list() {
        return registry.all().stream().map(this::toResponse).toList();
    }

    /**
     * Changes a service's versions.
     *
     * @param id               the service
     * @param modelVersion     the new baseline
     * @param canaryVersion    the new canary, or empty to clear
     * @param canaryPercentage the share of tracks on the canary
     * @return the updated service, or empty when nothing is registered under that id
     */
    public Optional<AiServiceResponse> setModelVersion(ServiceId id, Optional<String> modelVersion,
                                                       Optional<String> canaryVersion,
                                                       int canaryPercentage) {
        if (registry.find(id).isEmpty()) {
            return Optional.empty();
        }
        AiServiceDescriptor updated =
                registry.applyVersions(id, modelVersion, canaryVersion, canaryPercentage);
        log.info("AI service {} now baseline={} canary={}@{}%", id,
                updated.modelVersion().orElse("<service default>"),
                updated.canaryVersion().orElse("none"), updated.canaryPercentage());
        return Optional.of(toResponse(updated));
    }

    /**
     * Health is reported, never thrown.
     *
     * <p>An admin asking which services are up must get an answer about all of them, not a 500
     * because one is down — the unreachable one is precisely what they were looking for.
     */
    private AiServiceResponse toResponse(AiServiceDescriptor service) {
        boolean healthy;
        try {
            healthy = providers.forService(service).isHealthy(service);
        } catch (RuntimeException ex) {
            log.debug("Health probe of {} failed", service.id(), ex);
            healthy = false;
        }
        return new AiServiceResponse(
                service.id().value(),
                service.modalities().stream().map(Modality::name).sorted().toList(),
                service.mode().name(),
                service.endpoint().orElse(""),
                service.timeout().toMillis(),
                service.modelVersion().orElse(""),
                service.canaryVersion().orElse(""),
                service.canaryPercentage(),
                service.maxConcurrent(),
                healthy);
    }
}
