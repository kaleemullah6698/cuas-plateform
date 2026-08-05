package pk.nust.cuas.track.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * Where a track is going and what it is closing on — the derived half of a published track
 * (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Kept separate from {@link Track} on purpose. The aggregate is what the service *knows*, built
 * from detections and persisted; the outlook is what the service *infers* from that state, recomputed
 * on every publication and never stored. Storing it would create a second, staler copy of something
 * derivable in microseconds from the filter, and would have to be invalidated every time the asset
 * registry changed.
 *
 * @param predictedTrajectory the extrapolated path, in ascending time order; empty when the track is
 *                            too young or too uncertain to extrapolate usefully
 * @param nearestAssetCpa     the closest approach to the nearest protected asset, or empty when no
 *                            asset is configured
 */
public record TrackOutlook(
        List<PredictedPoint> predictedTrajectory, Optional<ClosestPointOfApproach> nearestAssetCpa) {

    private static final TrackOutlook NONE = new TrackOutlook(List.of(), Optional.empty());

    public TrackOutlook {
        if (predictedTrajectory == null || nearestAssetCpa == null) {
            throw new IllegalArgumentException("trajectory and CPA are required (use empty, not null)");
        }
        predictedTrajectory = List.copyOf(predictedTrajectory);
    }

    /** No prediction and no CPA — what a track carries when there is nothing useful to infer. */
    public static TrackOutlook none() {
        return NONE;
    }
}
