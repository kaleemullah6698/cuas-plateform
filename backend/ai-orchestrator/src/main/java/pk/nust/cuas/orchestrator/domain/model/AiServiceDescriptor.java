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
 * @param canaryVersion      a version to try on a share of traffic, or empty for no canary
 *                           (section 6.6)
 * @param canaryPercentage   the share of *tracks* that go to the canary, 0-100. Tracks, not calls:
 *                           see {@link pk.nust.cuas.orchestrator.domain.service.CanaryPolicy}
 */
public record AiServiceDescriptor(
        ServiceId id,
        Set<Modality> modalities,
        ProviderMode mode,
        Optional<String> endpoint,
        Duration timeout,
        Optional<String> modelVersion,
        int maxConcurrent,
        Optional<String> canaryVersion,
        int canaryPercentage) {

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
        if (endpoint == null || modelVersion == null || canaryVersion == null) {
            throw new IllegalArgumentException("use Optional.empty(), never null");
        }
        if (canaryPercentage < 0 || canaryPercentage > 100) {
            throw new IllegalArgumentException(
                    "service " + id + " canary percentage must be 0-100, was " + canaryPercentage);
        }
        // A canary share with no version to send it to would silently route traffic nowhere new.
        if (canaryPercentage > 0 && canaryVersion.isEmpty()) {
            throw new IllegalArgumentException("service " + id
                    + " declares a canary percentage but no canary version");
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

    /** A descriptor with no canary — the ordinary case, and what most tests want. */
    public static AiServiceDescriptor of(ServiceId id, Set<Modality> modalities, ProviderMode mode,
                                         Optional<String> endpoint, java.time.Duration timeout,
                                         Optional<String> modelVersion, int maxConcurrent) {
        return new AiServiceDescriptor(id, modalities, mode, endpoint, timeout, modelVersion,
                maxConcurrent, Optional.empty(), 0);
    }
}
