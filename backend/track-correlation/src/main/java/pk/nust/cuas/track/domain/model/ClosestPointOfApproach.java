package pk.nust.cuas.track.domain.model;

import java.time.Instant;

/**
 * How close a track will come to a protected asset, and when (ARCHITECTURE.md section 4.1.5).
 *
 * <p>This is the number the operator acts on — rendered as "CPA 640 m in 00:47" in section 2.4.2 —
 * and one of threat-assessment's scoring inputs (section 6.3.4). It is a kinematic projection of the
 * current estimate, not a statement of intent: it answers "if this track keeps doing what it is
 * doing, how close does it get", which is exactly the question an operator needs answered and
 * nothing more.
 *
 * <p>A receding track reports {@code closing = false}, {@code distanceM} as the present separation
 * and {@code timeToCpaS} zero, rather than a negative time. A consumer that ignored the sign of a
 * negative time would read a departing track as an imminent one; a consumer that ignores a boolean
 * reads {@code false}, which fails in the safe direction.
 *
 * @param asset      the asset this approach is measured against
 * @param distanceM  the horizontal separation at the closest point, in metres
 * @param timeToCpaS seconds from the estimate's time until that point; zero when already receding
 * @param at         when the closest point occurs; the estimate's own time when already receding
 * @param closing    whether the track is still closing on the asset
 */
public record ClosestPointOfApproach(
        ProtectedAsset asset, double distanceM, double timeToCpaS, Instant at, boolean closing) {

    public ClosestPointOfApproach {
        if (asset == null || at == null) {
            throw new IllegalArgumentException("asset and at are required");
        }
        if (distanceM < 0.0d || timeToCpaS < 0.0d) {
            throw new IllegalArgumentException("distance and time to CPA cannot be negative");
        }
    }
}
