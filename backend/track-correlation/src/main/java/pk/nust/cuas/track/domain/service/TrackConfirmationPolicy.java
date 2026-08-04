package pk.nust.cuas.track.domain.service;

/**
 * Decides when a tentative track has been seen enough times to be confirmed (ARCHITECTURE.md section
 * 4.1.5, tentative → confirmed).
 *
 * <p>The foundation confirms on a simple count: a track is confirmed once at least {@code threshold}
 * detections have associated to it. This is the count half of the classic M-of-N track-confirmation
 * rule; the "of N recent scans" window and the Kalman gate that make it robust to clutter arrive in
 * M5.2. Kept as a policy object so the rule is in one place and testable without a track.
 */
public final class TrackConfirmationPolicy {

    private final int threshold;

    /**
     * @param threshold the number of associated detections at which a track is confirmed; at least 2,
     *                  since a single detection is what created the track
     * @throws IllegalArgumentException when threshold is less than 2
     */
    public TrackConfirmationPolicy(int threshold) {
        if (threshold < 2) {
            throw new IllegalArgumentException("confirmation threshold must be at least 2, was " + threshold);
        }
        this.threshold = threshold;
    }

    /**
     * @param detectionCount the number of detections associated to the track, including the first
     * @return whether the track should be confirmed
     */
    public boolean shouldConfirm(int detectionCount) {
        return detectionCount >= threshold;
    }
}
