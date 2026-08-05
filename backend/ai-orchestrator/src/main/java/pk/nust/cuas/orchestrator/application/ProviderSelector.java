package pk.nust.cuas.orchestrator.application;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;

/**
 * Binds a service to the provider that serves it (ARCHITECTURE.md section 6.4).
 *
 * <p>The lookup is by {@link ProviderMode}, not by service id, and that is the whole mechanism behind
 * "provider switching is per-service and runtime-configurable". A service configured {@code mode:
 * mock} resolves to the mock; the same service reconfigured {@code mode: grpc} resolves to the gRPC
 * client, with no code aware that anything changed.
 *
 * <p>Keying by mode rather than by id is also what keeps the number of provider implementations
 * constant as models are added: fifty registered services still resolve to two providers. A map keyed
 * by service id would grow with every model and would have to be edited to add one — the code change
 * §6.1 forbids.
 *
 * <p>Completeness is checked at startup against the <em>configured</em> services rather than against
 * every mode that exists, so a deployment running only mocks does not need a gRPC client present.
 * See {@code OrchestrationConfig}.
 */
public class ProviderSelector {

    private final Map<ProviderMode, InferenceProvider> byMode = new EnumMap<>(ProviderMode.class);

    /**
     * @param providers the available providers, at most one per mode
     * @throws IllegalArgumentException when two providers claim the same mode
     */
    public ProviderSelector(Collection<InferenceProvider> providers) {
        for (InferenceProvider provider : providers) {
            InferenceProvider existing = byMode.put(provider.mode(), provider);
            if (existing != null) {
                throw new IllegalArgumentException(
                        "two providers claim mode " + provider.mode() + "; exactly one may");
            }
        }
    }

    /**
     * @param service the service to call
     * @return the provider bound to its configured mode
     * @throws IllegalStateException when nothing implements that mode — startup validation should
     *     have caught it, so reaching here means the registry and the providers disagree
     */
    public InferenceProvider forService(AiServiceDescriptor service) {
        InferenceProvider provider = byMode.get(service.mode());
        if (provider == null) {
            throw new IllegalStateException("no provider implements mode " + service.mode()
                    + ", which service " + service.id() + " is configured for");
        }
        return provider;
    }

    /** @return whether a provider is available for a mode; used by the startup check */
    public boolean supports(ProviderMode mode) {
        return byMode.containsKey(mode);
    }
}
