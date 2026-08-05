package pk.nust.cuas.track.domain.service;

import java.util.List;
import java.util.Optional;
import pk.nust.cuas.track.domain.model.ClosestPointOfApproach;
import pk.nust.cuas.track.domain.model.PredictedPoint;
import pk.nust.cuas.track.domain.model.ProtectedAsset;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.port.ProtectedAssetRegistry;

/**
 * Derives a track's forward-looking state — its predicted path and its closest approach to a
 * protected asset (ARCHITECTURE.md section 4.1.5).
 *
 * <p>One place where the two derivations are composed, so the rule about *when* a track is worth
 * extrapolating lives in the domain rather than being repeated at each publication site.
 *
 * <p>That rule: only a track the tracker has committed to gets an outlook. A {@code TENTATIVE}
 * track may be a sensor artefact that will never confirm, and its filter has seen too few
 * measurements for the velocity estimate to mean much; extrapolating it would put a confident-looking
 * trajectory and a CPA countdown on the operator's screen for something that may not exist. A
 * {@code LOST} track is not going anywhere. Confirmed and coasting tracks get both — coasting
 * especially, since a track being predicted forward is exactly when an operator wants to know where
 * it is heading.
 */
public final class TrackOutlookCalculator {

    private final TrajectoryPredictor predictor;
    private final CpaCalculator cpaCalculator;
    private final ProtectedAssetRegistry assets;

    public TrackOutlookCalculator(TrajectoryPredictor predictor, CpaCalculator cpaCalculator,
                                  ProtectedAssetRegistry assets) {
        this.predictor = predictor;
        this.cpaCalculator = cpaCalculator;
        this.assets = assets;
    }

    /**
     * @param track the track to look ahead for
     * @return its trajectory and CPA, or {@link TrackOutlook#none()} when the track is not one worth
     *     extrapolating
     */
    public TrackOutlook outlookFor(Track track) {
        if (!isWorthExtrapolating(track.status())) {
            return TrackOutlook.none();
        }
        List<PredictedPoint> trajectory =
                predictor.predict(track.filter(), track.estimate().observedAt());
        List<ProtectedAsset> configured = assets.all();
        Optional<ClosestPointOfApproach> cpa = configured.isEmpty()
                ? Optional.empty()
                : cpaCalculator.nearest(track.estimate().position(), track.estimate().velocity(),
                        track.estimate().observedAt(), configured);
        return new TrackOutlook(trajectory, cpa);
    }

    private static boolean isWorthExtrapolating(TrackStatus status) {
        return status == TrackStatus.CONFIRMED || status == TrackStatus.COASTING;
    }
}
