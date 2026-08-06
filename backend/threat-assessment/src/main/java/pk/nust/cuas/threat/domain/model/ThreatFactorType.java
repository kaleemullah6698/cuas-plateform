package pk.nust.cuas.threat.domain.model;

/**
 * The five weighted scoring factors (ARCHITECTURE.md section 6.3.4), rendered as the breakdown panel
 * in section 2.4.2.
 *
 * <p>An enum rather than a string key: the breakdown is an explainability surface an operator reads
 * and a reviewer audits, so a typo must not be able to produce a factor that nothing renders and
 * nothing weights. A policy's weight map is keyed by this type, which makes "every factor has a
 * weight" checkable at policy-validation time instead of at scoring time.
 */
public enum ThreatFactorType {

    /** Fused classification confidence, from {@code ai.inference.completed.v1}. */
    MODEL_CONFIDENCE,

    /** Closest-point-of-approach distance to the nearest protected asset. */
    PROXIMITY,

    /** Rate of closure along the CPA geometry; zero when the track is receding. */
    CLOSING_VELOCITY,

    /** Whether the track's approach has persisted rather than being a single sample. */
    TRAJECTORY_INTENT,

    /** Cross-service corroboration: how many reporting models agree on the label. */
    SENSOR_AGREEMENT
}
