package pk.nust.cuas.alert.domain.model;

/**
 * What was done to the aircraft. <strong>There is exactly one legal value, permanently.</strong>
 *
 * <p>This platform produces advisory recommendations only. RF jamming, GNSS spoofing and cyber
 * takeover are outside the software boundary, and every recommendation terminates at an operator
 * authorisation gate. Section 2.4.3's decision chain renders this as the literal "ENGAGEMENT ACTION
 * — NONE, outside system scope".
 *
 * <p>Modelling it as a one-value enum rather than a string makes that assertion structural: there is
 * no value a producer could set that claims otherwise, and an exported evidence bundle states the
 * fact rather than implying it by omission. A free-text field would have carried the same words with
 * none of the guarantee.
 *
 * <p>Adding a second value is an architecture amendment with supervisor approval, not a pull
 * request. {@code EngagementActionTest} and the contracts module's {@code AlertSerdeTest} both fail
 * the build if one appears.
 */
public enum EngagementAction {

    /** The only value. */
    NONE
}
