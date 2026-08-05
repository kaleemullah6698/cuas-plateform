package pk.nust.cuas.orchestrator.infrastructure.cache;

import java.util.Optional;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.orchestrator.application.port.InferenceCache;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * The inference cache for a deployment without Redis: every read is a miss, every write discarded.
 *
 * <p>Not a test double. The {@link InferenceCache} contract is written so a permanent miss is correct
 * behaviour, and that claim is only worth something if a configuration exercises it. Inference runs
 * identically here — every call reaches a provider: slower, never wrong.
 */
public class NoInferenceCache implements InferenceCache {

    @Override
    public Optional<InferenceResponse> get(ServiceId service, String key) {
        return Optional.empty();
    }

    @Override
    public void put(ServiceId service, String key, InferenceResponse response) {
        // Nothing is cached, so there is nothing to write.
    }
}
