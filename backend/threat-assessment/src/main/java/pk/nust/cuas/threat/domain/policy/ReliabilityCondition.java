package pk.nust.cuas.threat.domain.policy;

/**
 * Whether a {@link RecommendationRule} cares about the reliability of the score it is advising on.
 *
 * <p>Both directions are needed, which is why this is an enum rather than a boolean. Most rules are
 * indifferent; a few fire <em>only</em> on a reliable score, because advising confidently on thin
 * evidence is the failure section 6.3.4's confidence requirement exists to prevent; and at least one
 * fires only on an <em>unreliable</em> one, to say so out loud rather than let a partial score reach
 * the operator dressed as a complete one.
 */
public enum ReliabilityCondition {

    /** The rule does not depend on reliability. */
    ANY,

    /** The rule fires only when the score met the policy's reliability floor. */
    RELIABLE_ONLY,

    /** The rule fires only when it did not. */
    UNRELIABLE_ONLY;

    /**
     * @param isReliable whether the score met the floor
     * @return {@code true} when this condition is satisfied
     */
    public boolean isSatisfiedBy(boolean isReliable) {
        return switch (this) {
            case ANY -> true;
            case RELIABLE_ONLY -> isReliable;
            case UNRELIABLE_ONLY -> !isReliable;
        };
    }
}
