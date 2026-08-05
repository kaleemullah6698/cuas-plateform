package pk.nust.cuas.orchestrator.domain.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * The registered AI services, and which modalities each accepts (ARCHITECTURE.md section 6.1).
 *
 * <p>Built once from configuration and immutable thereafter. It is the only thing in the orchestrator
 * that knows a service exists, and it knows nothing about any service beyond its descriptor — so
 * <b>adding a model is adding a map entry</b>, which is exactly what section 6.1 promises and what
 * makes radar fusion, acoustic, or GNSS additions configuration-only.
 *
 * <p>Configuration order is preserved so routing is deterministic: two services accepting the same
 * modality are always offered in the order they were declared, which keeps a fan-out reproducible in
 * a test and in a demo rather than dependent on hash iteration order.
 *
 * <p>Pure and framework-free.
 */
public final class AiServiceRegistry {

    private final Map<ServiceId, AiServiceDescriptor> byId;
    private final List<AiServiceDescriptor> ordered;

    /**
     * @param services the configured services
     * @throws IllegalArgumentException when two services share an id
     */
    public AiServiceRegistry(Collection<AiServiceDescriptor> services) {
        Map<ServiceId, AiServiceDescriptor> map = new LinkedHashMap<>();
        for (AiServiceDescriptor service : services) {
            if (map.put(service.id(), service) != null) {
                throw new IllegalArgumentException("duplicate AI service id: " + service.id());
            }
        }
        this.byId = Map.copyOf(map);
        this.ordered = List.copyOf(map.values());
    }

    /**
     * The services that accept a modality, in configuration order.
     *
     * @param modality the detection modality to route
     * @return the matching services; empty when nothing is configured for it, which is a normal
     *     state rather than an error — a deployment need not run a model for every modality
     */
    public List<AiServiceDescriptor> servicesFor(Modality modality) {
        return ordered.stream().filter(service -> service.accepts(modality)).toList();
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
        return ordered;
    }

    /** @return whether any service is registered at all */
    public boolean isEmpty() {
        return ordered.isEmpty();
    }
}
