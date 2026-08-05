package pk.nust.cuas.orchestrator.application.port;

import java.util.Optional;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * Caches inference results by the content of their input (ARCHITECTURE.md section 4.1.6, "Redis for
 * inference cache (key = content hash of input, TTL 60 s)").
 *
 * <p><b>Content-addressed, not track-addressed.</b> The key is a hash of what was actually sent — the
 * detections and the options — so a repeat of *the same observation* is served from cache while a
 * track that has genuinely moved is not. Keying by {@code trackId} would be the obvious shortcut and
 * would be wrong in the one direction that matters: a track is a thing that changes, so its second
 * inference is usually a different question, and answering it from the first would freeze a moving
 * drone's classification at whatever it was when first seen.
 *
 * <p>Section 6.5 explains why this exists at all: inference is expensive, and "a recent cached result
 * beats a duplicate GPU call". The short TTL bounds how stale an answer can be.
 *
 * <p><b>Advisory in both directions.</b> A miss and an unreachable cache are the same thing to the
 * caller, and a write failure is dropped. Like the track hot-state store (§4.1.5), a cache outage
 * must cost latency, never correctness — refusing to infer because Redis is down would turn a
 * degraded system into an unscored drone.
 *
 * <p><b>Degraded results are never cached.</b> A fallback answer is a statement about the *service's*
 * health at one moment, not about the input; caching it would keep serving a mock answer for a full
 * TTL after the real service recovered.
 */
public interface InferenceCache {

    /**
     * @param service the service the question was for — part of the key, since the same input asked
     *                of two services is two different questions
     * @param key     the content hash of the request
     * @return the cached response, or empty on a miss or when the cache is unreachable
     */
    Optional<InferenceResponse> get(ServiceId service, String key);

    /**
     * @param service  the service the answer came from
     * @param key      the content hash of the request
     * @param response the response to remember
     */
    void put(ServiceId service, String key, InferenceResponse response);
}
