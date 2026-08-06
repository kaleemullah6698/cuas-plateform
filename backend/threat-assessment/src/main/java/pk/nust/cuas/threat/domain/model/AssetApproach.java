package pk.nust.cuas.threat.domain.model;

/**
 * A track's closest point of approach to one protected asset, as published on the track topics
 * (ARCHITECTURE.md section 5.3.2, {@code ClosestPointOfApproach}).
 *
 * <p>The geometry two of the five scoring factors are computed from. It is a pure kinematic
 * projection under the track's current estimate — not a prediction of intent — so this type carries
 * no judgement of its own; {@link ThreatFactorType#PROXIMITY} and
 * {@link ThreatFactorType#CLOSING_VELOCITY} are where it becomes evidence.
 *
 * @param assetId      the protected asset
 * @param assetName    what the operator sees
 * @param distanceM    separation at the closest point
 * @param timeToCpaS   seconds from the track's observation time until that point
 * @param closing      {@code false} when the track is already receding, in which case
 *                     {@code distanceM} is the present separation and {@code timeToCpaS} is zero
 */
public record AssetApproach(
        String assetId,
        String assetName,
        double distanceM,
        double timeToCpaS,
        boolean closing) {

    public AssetApproach {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("An approach must name the asset it is measured to");
        }
        if (distanceM < 0.0 || Double.isNaN(distanceM)) {
            throw new IllegalArgumentException("CPA distance must be non-negative, was " + distanceM);
        }
        if (timeToCpaS < 0.0 || Double.isNaN(timeToCpaS)) {
            throw new IllegalArgumentException(
                    "Time to CPA must be non-negative; a receding track reports closing=false"
                            + " rather than a negative time. Was " + timeToCpaS);
        }
    }

    /**
     * The track's rate of closure on the asset, in metres per second.
     *
     * <p>Derived, not measured. Under the same constant-velocity geometry the CPA itself assumes, a
     * track travelling at {@code groundSpeedMs} covers {@code groundSpeedMs × timeToCpaS} metres
     * before reaching a closest approach of {@code distanceM}, so its present separation is the
     * hypotenuse of those two legs. The closure rate is then the separation lost per second on the
     * way there. This is exact given the inputs rather than an approximation of them — the track
     * contract carries the CPA geometry and the ground speed but no instantaneous range rate, and
     * substituting ground speed directly would overstate closure for a track passing across the
     * asset rather than at it.
     *
     * @param groundSpeedMs the track's estimated ground speed
     * @return the closure rate, or zero when the track is receding or already at its closest point
     */
    public double closureRateMs(double groundSpeedMs) {
        if (!closing || timeToCpaS <= 0.0) {
            return 0.0;
        }
        double alongTrackM = groundSpeedMs * timeToCpaS;
        double presentSeparationM = Math.hypot(distanceM, alongTrackM);
        return (presentSeparationM - distanceM) / timeToCpaS;
    }
}
