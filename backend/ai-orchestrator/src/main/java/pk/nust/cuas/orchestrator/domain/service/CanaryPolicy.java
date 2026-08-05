package pk.nust.cuas.orchestrator.domain.service;

import java.util.Optional;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;

/**
 * Decides which model version a request is asked of (ARCHITECTURE.md section 6.6, canary).
 *
 * <p>§6.6: "Orchestrator splits traffic by {@code trackId} hash: e.g. 90% v1.2.0 / 10% v1.3.0."
 *
 * <p><b>By track, not per call.</b> A track that lands on the canary stays on the canary for its
 * whole life. Splitting per request would mean one drone's successive classifications came from two
 * different models, so a change in its threat level could be the model changing rather than the
 * situation — and an operator would have no way to tell which. Track-stable assignment also makes a
 * comparison meaningful: two populations of *tracks*, not two populations of calls.
 *
 * <p>Deterministic, from the same FNV-1a seeding the mock uses, so a track's assignment is
 * reproducible across restarts and across instances. A {@link java.util.Random} would put the same
 * track on different models on different orchestrator instances, which would make the canary's
 * results meaningless.
 *
 * <p>No model is named here. The policy knows only that a service has a baseline version and
 * possibly a canary version with a share — both configuration.
 *
 * <p>Pure and framework-free.
 */
public final class CanaryPolicy {

    /**
     * Chooses the version for one request.
     *
     * @param service the service being called
     * @param trackId the track the request is about
     * @return the version to pin, or empty to let the service use its default. Empty is returned
     *     when no canary is configured *and* no baseline is pinned — the ordinary case, where the
     *     deployed model decides.
     */
    public Optional<String> versionFor(AiServiceDescriptor service, String trackId) {
        Optional<String> canary = service.canaryVersion();
        if (canary.isEmpty() || service.canaryPercentage() <= 0) {
            return service.modelVersion();
        }
        return isCanary(service, trackId) ? canary : service.modelVersion();
    }

    /**
     * @param service the service
     * @param trackId the track
     * @return whether this track falls in the canary share
     */
    public boolean isCanary(AiServiceDescriptor service, String trackId) {
        int share = service.canaryPercentage();
        if (share <= 0 || service.canaryVersion().isEmpty()) {
            return false;
        }
        if (share >= 100) {
            return true;
        }
        // The service id is part of the seed so a track is not systematically on the canary of every
        // service at once — that would correlate the experiments and make one model's regression look
        // like another's.
        long seed = DeterministicSeed.of("canary", service.id().value(), trackId);
        return Math.floorMod(seed, 100L) < share;
    }
}
