package pk.nust.cuas.orchestrator.domain.model;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the orchestrator knows about one registered AI service (ARCHITECTURE.md sections 6.1,
 * 6.4).
 *
 * <p>This is the whole of it. There is no subclass per model, no switch on the id, and no field that
 * only one kind of service has — because section 6.1 promises that adding a model is a configuration
 * entry, and that promise dies the moment the orchestrator needs to know which model it is talking
 * to. A radar-fusion service, an acoustic classifier, or a GNSS anomaly detector are all *this*
 * record with different values.
 *
 * @param id            the service's configured identity
 * @param modalities    which detection modalities it accepts; a request routes here when it carries
 *                      at least one of them
 * @param mode          mock or gRPC
 * @param endpoint      {@code host:port}, required for {@link ProviderMode#GRPC} and absent for mock
 * @param timeout       the orchestrator-enforced budget for one call (section 6.3's per-service p95)
 * @param modelVersion  the version to pin, or empty for the service default — the canary and
 *                      rollback lever of section 6.6
 * @param maxConcurrent the bulkhead permit count, bounding concurrency so a burst never overwhelms
 *                      a GPU (section 4.1.6)
 */
public record AiServiceDescriptor(
        ServiceId id,
        Set<Modality> modalities,
        ProviderMode mode,
        Optional<String> endpoint,
        Duration timeout,
        Optional<String> modelVersion,
        int maxConcurrent) {

    public AiServiceDescriptor {
        if (id == null) {
            throw new IllegalArgumentException("a service id is required");
        }
        if (modalities == null || modalities.isEmpty()) {
            throw new IllegalArgumentException(
                    "service " + id + " must accept at least one modality, or nothing would route to it");
        }
        if (mode == null) {
            throw new IllegalArgumentException("service " + id + " must declare a provider mode");
        }
        if (endpoint == null || modelVersion == null) {
            throw new IllegalArgumentException("use Optional.empty(), never null");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("service " + id + " needs a positive timeout, was " + timeout);
        }
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException(
                    "service " + id + " needs at least one bulkhead permit, was " + maxConcurrent);
        }
        // A gRPC service with no endpoint would fail on its first call, at the worst possible moment.
        // Rejecting it at startup turns a runtime outage into a configuration error.
        if (mode == ProviderMode.GRPC && endpoint.isEmpty()) {
            throw new IllegalArgumentException("service " + id + " is mode=grpc but declares no endpoint");
        }
        modalities = Set.copyOf(modalities);
    }

    /**
     * @param modality a detection modality
     * @return whether this service accepts it
     */
    public boolean accepts(Modality modality) {
        return modalities.contains(modality);
    }
}
