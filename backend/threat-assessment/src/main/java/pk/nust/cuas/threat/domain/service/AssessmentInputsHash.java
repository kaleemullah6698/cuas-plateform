package pk.nust.cuas.threat.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * A content hash over everything that determines an assessment's outcome — the idempotency key for
 * at-least-once redelivery.
 *
 * <p>Kafka delivers at least once (section 5.7), so the same {@code track.updated.v1} record can be
 * processed twice. Keying only on {@code (trackId, observedAt)} would be the obvious guard and would
 * be wrong in the other direction: a new inference result arriving for the same track estimate is a
 * genuinely different question and must produce a new assessment, but shares that key exactly. So
 * the key is the <em>content</em>: identical inputs and identical policy mean an identical answer,
 * and computing it again is waste; anything else is a new assessment.
 *
 * <p>The same reasoning and the same construction as the orchestrator's request hash (section 4.1.6)
 * — length-prefixed fields so no concatenation of two values can collide with a different pair, and
 * the policy version in the digest because the same evidence scored under a retuned policy is a
 * different claim.
 *
 * <p>The assessment id, the correlation id and the wall clock are deliberately excluded. They differ
 * on every delivery of the same record, and including them would defeat the check entirely.
 */
public final class AssessmentInputsHash {

    private static final String ALGORITHM = "SHA-256";

    private AssessmentInputsHash() {
    }

    /**
     * @param evidence the scoring inputs
     * @param policy   the policy in force
     * @return the lowercase hex digest
     */
    public static String of(TrackEvidence evidence, ThreatPolicy policy) {
        MessageDigest digest = newDigest();

        update(digest, policy.policyId());
        update(digest, policy.version());

        evidence.snapshot().ifPresentOrElse(snapshot -> {
            update(digest, snapshot.trackId().toString());
            update(digest, snapshot.sectorId());
            update(digest, Long.toString(snapshot.observedAt().toEpochMilli()));
            update(digest, Double.toString(snapshot.speedMs()));
            snapshot.approach().ifPresentOrElse(cpa -> {
                update(digest, cpa.assetId());
                update(digest, Double.toString(cpa.distanceM()));
                update(digest, Double.toString(cpa.timeToCpaS()));
                update(digest, Boolean.toString(cpa.closing()));
            }, () -> update(digest, "no-approach"));
        }, () -> update(digest, "no-snapshot"));

        update(digest, Integer.toString(evidence.consecutiveClosingCount()));

        // Sorted by model id so the digest does not depend on the order results happened to arrive.
        List<InferenceObservation> ordered = evidence.observations().stream()
                .sorted(java.util.Comparator.comparing(InferenceObservation::modelId))
                .toList();
        update(digest, Integer.toString(ordered.size()));
        for (InferenceObservation observation : ordered) {
            update(digest, observation.modelId());
            update(digest, observation.modelVersion());
            update(digest, observation.label());
            update(digest, Double.toString(observation.confidence()));
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Length-prefixes each field before digesting it, so {@code ("ab", "c")} and {@code ("a", "bc")}
     * cannot produce the same digest.
     */
    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[]{
                (byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8), (byte) bytes.length});
        digest.update(bytes);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JDK specification; its absence is not a recoverable state.
            throw new IllegalStateException(ALGORITHM + " is not available", ex);
        }
    }
}
