package pk.nust.cuas.threat.domain.policy;

import pk.nust.cuas.threat.domain.model.ThreatLevel;

/**
 * The composite-score boundaries between threat levels (ARCHITECTURE.md section 6.3.4).
 *
 * <p>Boundaries are lower-inclusive and upper-exclusive, so every score in {@code [0,1]} maps to
 * exactly one level with no gap and no overlap. {@code CRITICAL >= 0.80} is section 2.4.2's own
 * value and is what anchors the scale; the other two were absent from the specification until the
 * M7.A amendment and were chosen to reproduce section 2.4.2's rendered rows rather than invented.
 *
 * <p>These are <em>policy data</em>, not constants. Retuning a deployment's sensitivity is a new
 * policy version with an audit trail, never a redeploy, and every assessment records the version
 * that produced it so a past score stays reproducible.
 *
 * @param medium the score at or above which a track is MEDIUM
 * @param high   the score at or above which a track is HIGH
 * @param critical the score at or above which a track is CRITICAL
 */
public record LevelThresholds(double medium, double high, double critical) {

    /** Section 6.3.4's normative defaults: LOW &lt; 0.30, MEDIUM 0.30–0.55, HIGH 0.55–0.80, CRITICAL ≥ 0.80. */
    public static final LevelThresholds DEFAULT = new LevelThresholds(0.30, 0.55, 0.80);

    public LevelThresholds {
        if (!(medium > 0.0) || !(critical < 1.0)) {
            throw new IllegalArgumentException(
                    "Thresholds must leave a non-empty LOW band below and CRITICAL band above:"
                            + " medium=" + medium + " critical=" + critical);
        }
        if (!(medium < high && high < critical)) {
            throw new IllegalArgumentException(
                    "Thresholds must be strictly ascending, were medium=" + medium
                            + " high=" + high + " critical=" + critical);
        }
    }

    /**
     * @param compositeScore the weighted composite, in {@code [0,1]}
     * @return the level that score falls in
     */
    public ThreatLevel levelFor(double compositeScore) {
        if (compositeScore >= critical) {
            return ThreatLevel.CRITICAL;
        }
        if (compositeScore >= high) {
            return ThreatLevel.HIGH;
        }
        if (compositeScore >= medium) {
            return ThreatLevel.MEDIUM;
        }
        return ThreatLevel.LOW;
    }
}
