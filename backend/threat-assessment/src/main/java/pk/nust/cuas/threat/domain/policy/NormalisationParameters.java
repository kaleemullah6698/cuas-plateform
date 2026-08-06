package pk.nust.cuas.threat.domain.policy;

/**
 * The scale each raw measurement is normalised against before it is weighted.
 *
 * <p>Section 6.3.4 fixes the factors and their weights but says nothing about the curve that turns
 * "640 metres" into "0.92". These parameters are that curve, and they are policy data for the same
 * reason the thresholds are: a deployment protecting a airfield and one protecting a single building
 * disagree about what counts as close, and neither should need a redeploy to say so.
 *
 * <p>Every curve below is <strong>linear and clamped</strong> rather than exponential or sigmoid.
 * That is a deliberate constraint, not a simplification: section 6.3.4 requires the score to be
 * explainable to an operator and defensible to a reviewer, and a reviewer can check a linear ramp
 * against the number in front of them. A curve nobody can evaluate by hand is a curve nobody can
 * challenge.
 *
 * @param proximityCriticalRangeM  the CPA distance at which proximity scores 1.0; anything at or
 *                                 beyond {@code proximityIgnoreRangeM} scores 0.0
 * @param proximityIgnoreRangeM    the CPA distance beyond which proximity contributes nothing
 * @param closureReferenceMs       the closure rate that scores 1.0; faster is clamped
 * @param intentConfirmationSweeps consecutive closing track updates at which trajectory intent
 *                                 scores 1.0
 */
public record NormalisationParameters(
        double proximityCriticalRangeM,
        double proximityIgnoreRangeM,
        double closureReferenceMs,
        int intentConfirmationSweeps) {

    /**
     * Defaults sized to section 2.4.2's worked example: a track at CPA 640 m scores 0.92 proximity,
     * and one closing at roughly 14 m/s scores 0.78.
     */
    public static final NormalisationParameters DEFAULT =
            new NormalisationParameters(200.0, 5_000.0, 18.0, 4);

    public NormalisationParameters {
        if (!(proximityCriticalRangeM > 0.0) || !(proximityIgnoreRangeM > proximityCriticalRangeM)) {
            throw new IllegalArgumentException(
                    "Proximity ranges must be positive and ascending: critical="
                            + proximityCriticalRangeM + " ignore=" + proximityIgnoreRangeM);
        }
        if (!(closureReferenceMs > 0.0)) {
            throw new IllegalArgumentException(
                    "Closure reference speed must be positive, was " + closureReferenceMs);
        }
        if (intentConfirmationSweeps < 1) {
            throw new IllegalArgumentException(
                    "Intent confirmation needs at least one sweep, was " + intentConfirmationSweeps);
        }
    }

    /**
     * @param distanceM the CPA distance
     * @return the normalised proximity, 1.0 at or inside the critical range falling linearly to 0.0
     *         at the ignore range
     */
    public double normaliseProximity(double distanceM) {
        if (distanceM <= proximityCriticalRangeM) {
            return 1.0;
        }
        if (distanceM >= proximityIgnoreRangeM) {
            return 0.0;
        }
        double span = proximityIgnoreRangeM - proximityCriticalRangeM;
        return (proximityIgnoreRangeM - distanceM) / span;
    }

    /**
     * @param closureRateMs the rate of closure along the CPA geometry
     * @return the normalised closure, linear to 1.0 at the reference rate
     */
    public double normaliseClosure(double closureRateMs) {
        if (closureRateMs <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, closureRateMs / closureReferenceMs);
    }

    /**
     * @param consecutiveClosingCount how many consecutive updates have shown the track closing
     * @return the normalised intent, linear to 1.0 at the confirmation count
     */
    public double normaliseIntent(int consecutiveClosingCount) {
        if (consecutiveClosingCount <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) consecutiveClosingCount / intentConfirmationSweeps);
    }
}
