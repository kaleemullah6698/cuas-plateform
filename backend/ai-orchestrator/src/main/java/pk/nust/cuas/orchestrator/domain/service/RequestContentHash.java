package pk.nust.cuas.orchestrator.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Hashes the *content* of an inference request (ARCHITECTURE.md section 4.1.6, "key = content hash of
 * input").
 *
 * <p>Framework-free and contract-free: it takes the already-extracted parts rather than an
 * {@code InferenceRequest}, so the domain keeps compiling without the generated types and the rule
 * for <em>what counts as content</em> is written down in one place.
 *
 * <p><b>What is deliberately excluded matters more than what is included.</b> The request id and the
 * correlation id are omitted: they differ on every call by construction, so including them would make
 * every key unique and the cache would never hit. The track id is omitted too — a track is a thing
 * that changes, and keying on it would serve the first classification of a moving drone for the whole
 * TTL. What remains is the observation itself: the detections, and the options that change what the
 * question means.
 *
 * <p>SHA-256 rather than a fast non-cryptographic hash. The cost is negligible beside an inference,
 * and a collision here does not merely waste work — it would return <em>one track's classification
 * for another's detections</em>. That is a wrong answer about the airspace, and it is worth the few
 * microseconds to make it infeasible.
 */
public final class RequestContentHash {

    private RequestContentHash() {
    }

    /**
     * @param detectionFingerprints one stable string per detection, in the order sent
     * @param modelVersion          the pinned version, or empty for the service default — a canary
     *                              asks a different model, so it must not share a key
     * @param minConfidence         the suppression threshold, which changes which predictions come back
     * @param includeFeatures       whether embeddings were requested
     * @param explain               whether attributions were requested
     * @return a hex SHA-256 digest
     */
    public static String of(List<String> detectionFingerprints, String modelVersion,
                            float minConfidence, boolean includeFeatures, boolean explain) {
        StringBuilder content = new StringBuilder();
        for (String fingerprint : detectionFingerprints) {
            // A length prefix, so ["ab","c"] and ["a","bc"] cannot produce the same digest.
            content.append(fingerprint.length()).append(':').append(fingerprint).append(';');
        }
        content.append("|v=").append(modelVersion)
                .append("|c=").append(minConfidence)
                .append("|f=").append(includeFeatures)
                .append("|e=").append(explain);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS; absent it, the JRE is unusable.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
