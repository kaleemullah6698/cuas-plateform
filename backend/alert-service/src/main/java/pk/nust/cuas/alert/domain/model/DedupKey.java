package pk.nust.cuas.alert.domain.model;

import java.util.UUID;

/**
 * The identity that decides whether a threat transition is a new alert or a recurrence of an open
 * one (ARCHITECTURE.md sections 4.1.8, 5.7).
 *
 * <p><strong>Section 5.7 names duplicate alerts as the failure that destroys operator trust faster
 * than any other.</strong> Everything about this type follows from taking that literally: it is a
 * value object so it cannot be assembled inconsistently in two places, it is published on the event
 * so a consumer can deduplicate the way this service does, and it is backed by a unique constraint in
 * {@code alertdb} — application logic alone cannot survive two instances processing the same
 * transition concurrently.
 *
 * <p><strong>The key is track + severity + policy, and each part earns its place.</strong>
 * <ul>
 *   <li><b>Track</b>, because two drones are two alerts however similar their behaviour.</li>
 *   <li><b>Severity</b>, because a track climbing from HIGH to CRITICAL is genuinely new
 *       information an operator must see; folding it into the open HIGH alert would silently
 *       downgrade it.</li>
 *   <li><b>Policy version</b>, because the same track under a retuned policy is a different
 *       judgement. Without it, a policy change would be invisible to anyone watching the alert
 *       queue — the open alert would simply persist, apparently unchanged, under rules that no
 *       longer applied.</li>
 * </ul>
 *
 * @param trackId       the track the alert is about
 * @param severity      the level that raised it
 * @param policyVersion the scoring policy version in force
 */
public record DedupKey(UUID trackId, Severity severity, String policyVersion) {

    private static final String SEPARATOR = "|";

    public DedupKey {
        if (trackId == null || severity == null) {
            throw new IllegalArgumentException("A dedup key needs a track and a severity");
        }
        policyVersion = policyVersion == null ? "" : policyVersion;
    }

    /**
     * @return the stored and published form. A pipe separator is safe here because none of the three
     *         parts can contain one: a UUID and an enum name cannot, and a policy version is
     *         constrained at the API boundary.
     */
    public String value() {
        return trackId + SEPARATOR + severity.name() + SEPARATOR + policyVersion;
    }

    @Override
    public String toString() {
        return value();
    }
}
