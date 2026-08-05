package pk.nust.cuas.orchestrator.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * The registered AI services, and which modalities each accepts (ARCHITECTURE.md section 6.1).
 *
 * <p>Seeded from configuration at startup. It is the only thing in the orchestrator that knows a
 * service exists, and it knows nothing about any service beyond its descriptor — so <b>adding a model
 * is adding a map entry</b>, which is exactly what §6.1 promises and what makes radar fusion,
 * acoustic, or GNSS additions configuration-only.
 *
 * <p>Configuration order is preserved so routing is deterministic: two services accepting the same
 * modality are always offered in the order they were declared, which keeps a fan-out reproducible in
 * a test and in a demo rather than dependent on hash iteration order.
 *
 * <p><b>The set of services is fixed; a service's model version is not.</b> §4.1.6 gives the
 * orchestrator {@code POST /ai/services/{id}/model-version}, and §6.6 makes rollback "a configuration
 * change — no redeploy, no code change", so the version has to be changeable while running. Only the
 * version fields move: an id, its modalities, its mode and its endpoint are the deployment's shape
 * and changing those at runtime would let an operator reroute the pipeline through an HTTP call.
 * That is why {@link #applyVersions} takes exactly three fields and nothing else.
 *
 * <p>Framework-free. Concurrent because the admin endpoint writes while the inference path reads.
 */
public final class AiServiceRegistry {

    private final Map<ServiceId, AiServiceDescriptor> byId = new ConcurrentHashMap<>();
    private final List<ServiceId> order;

    /**
     * @param services the configured services
     * @throws IllegalArgumentException when two services share an id
     */
    public AiServiceRegistry(Collection<AiServiceDescriptor> services) {
        Map<ServiceId, AiServiceDescriptor> seeded = new LinkedHashMap<>();
        for (AiServiceDescriptor service : services) {
            if (seeded.put(service.id(), service) != null) {
                throw new IllegalArgumentException("duplicate AI service id: " + service.id());
            }
        }
        byId.putAll(seeded);
        this.order = List.copyOf(seeded.keySet());
    }

    /**
     * The services that accept a modality, in configuration order.
     *
     * @param modality the detection modality to route
     * @return the matching services; empty when nothing is configured for it, which is a normal
     *     state rather than an error — a deployment need not run a model for every modality
     */
    public List<AiServiceDescriptor> servicesFor(Modality modality) {
        return all().stream().filter(service -> service.accepts(modality)).toList();
    }

    /**
     * @param id a service id
     * @return its descriptor, or empty when nothing is registered under that id
     */
    public Optional<AiServiceDescriptor> find(ServiceId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** @return every registered service, in configuration order */
    public List<AiServiceDescriptor> all() {
        List<AiServiceDescriptor> services = new ArrayList<>(order.size());
        for (ServiceId id : order) {
            AiServiceDescriptor service = byId.get(id);
            if (service != null) {
                services.add(service);
            }
        }
        return List.copyOf(services);
    }

    /** @return whether any service is registered at all */
    public boolean isEmpty() {
        return order.isEmpty();
    }

    /**
     * Changes a service's model versions at runtime (§4.1.6, §6.6).
     *
     * @param id               the service to change
     * @param modelVersion     the new baseline, or empty for the service default
     * @param canaryVersion    the new canary, or empty to clear it
     * @param canaryPercentage the share of tracks on the canary
     * @return the updated descriptor
     * @throws IllegalArgumentException when the id is not registered, or the canary settings are
     *     inconsistent — the descriptor's own invariants apply, so a percentage without a version is
     *     rejected here rather than silently routing traffic nowhere new
     */
    public AiServiceDescriptor applyVersions(ServiceId id, Optional<String> modelVersion,
                                             Optional<String> canaryVersion, int canaryPercentage) {
        AiServiceDescriptor current = byId.get(id);
        if (current == null) {
            throw new IllegalArgumentException("no AI service is registered as '" + id + "'");
        }
        AiServiceDescriptor updated = new AiServiceDescriptor(
                current.id(), current.modalities(), current.mode(), current.endpoint(),
                current.timeout(), modelVersion, current.maxConcurrent(),
                canaryVersion, canaryPercentage);
        byId.put(id, updated);
        return updated;
    }
}
