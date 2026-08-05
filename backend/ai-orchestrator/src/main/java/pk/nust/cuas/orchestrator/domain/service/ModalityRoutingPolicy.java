package pk.nust.cuas.orchestrator.domain.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;

/**
 * Chooses which AI services an inference request goes to (ARCHITECTURE.md sections 4.1.6, 6.1).
 *
 * <p>Routing is by the modalities present in the request's detections, and by nothing else. A request
 * carrying RF and radar observations reaches every service that accepts RF or radar — including a
 * fusion service that accepts both, which is how a multi-modal model gets its input without the
 * orchestrator knowing it is a fusion model.
 *
 * <p>A service is returned at most once even when it accepts several of the request's modalities;
 * otherwise a two-modality request would call a fusion service twice, doubling GPU load and producing
 * two competing results for one track.
 *
 * <p>Pure and framework-free: the same detections and the same registry always give the same route.
 */
public final class ModalityRoutingPolicy {

    private final AiServiceRegistry registry;

    public ModalityRoutingPolicy(AiServiceRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param modalities the distinct modalities present in the request's detections
     * @return the services to call, in configuration order, each appearing once; empty when no
     *     registered service accepts any of these modalities
     */
    public List<AiServiceDescriptor> route(Collection<Modality> modalities) {
        Set<AiServiceDescriptor> selected = new LinkedHashSet<>();
        for (Modality modality : modalities) {
            selected.addAll(registry.servicesFor(modality));
        }
        return List.copyOf(selected);
    }
}
