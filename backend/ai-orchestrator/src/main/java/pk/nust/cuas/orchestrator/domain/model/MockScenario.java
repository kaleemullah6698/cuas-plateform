package pk.nust.cuas.orchestrator.domain.model;

/**
 * The scenario library the mock draws from (ARCHITECTURE.md section 6.4).
 *
 * <p>Six situations chosen because each drives a distinct operator-facing state. Between them they
 * exercise every degraded path in the dashboard <em>before</em> a real model exists to produce one —
 * which is the point of the mock being a first-class deliverable rather than a stub returning a
 * constant.
 *
 * <p>A track is assigned a scenario deterministically from its id, so the same track always behaves
 * the same way. That is what makes a demonstration repeatable and a failing test reproducible.
 */
public enum MockScenario {

    /** One confident drone classification — the ordinary case. */
    SINGLE_INTRUSION,

    /** Several airframes at once, testing multi-target rendering and alert grouping. */
    SWARM,

    /** A confident *non*-drone: the false positive an operator must be able to dismiss. */
    FALSE_POSITIVE_BIRD,

    /** Not enough input to infer from — exercises the "factor missing" path of section 6.3.4. */
    SENSOR_DROPOUT,

    /** Ran fine, but genuinely uncertain: low confidence, high entropy, self-reported unreliable. */
    LOW_CONFIDENCE_AMBIGUOUS,

    /** Two plausible classes with near-equal confidence — the disagreement case of section 2.5.6. */
    CONTRADICTORY_MODALITIES
}
