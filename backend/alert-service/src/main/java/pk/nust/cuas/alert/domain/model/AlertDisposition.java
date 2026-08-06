package pk.nust.cuas.alert.domain.model;

/**
 * How an alert ended (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Carried into reporting, where the ratio of {@code TRUE_POSITIVE} to {@code FALSE_POSITIVE} is
 * the measure of whether the section 6.3.4 scoring policy is calibrated for this airspace.
 *
 * <p>{@code INCONCLUSIVE} exists deliberately. Forcing a verdict the operator does not have would
 * corrupt exactly the statistic this field is here to produce, and an operator who must choose
 * between two wrong answers will pick the one that closes the alert fastest.
 */
public enum AlertDisposition {

    /** A real, unauthorised UAS. */
    TRUE_POSITIVE,

    /** Nothing was there; the pipeline produced a track from noise. */
    FALSE_POSITIVE,

    /** Something was there and it was not a threat — a bird, a permitted flight. */
    BENIGN,

    /** The same object as another alert, recognised only after the fact. */
    DUPLICATE,

    /** The operator could not determine which of the above it was. */
    INCONCLUSIVE
}
