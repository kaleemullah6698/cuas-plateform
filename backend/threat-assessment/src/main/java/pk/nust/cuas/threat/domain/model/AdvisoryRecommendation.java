package pk.nust.cuas.threat.domain.model;

/**
 * What the system advises, why, and which rule said so.
 *
 * <p>The three travel together because a recommendation an operator cannot see the reasoning for is
 * one they can only obey or ignore, and a recommendation a reviewer cannot trace to a rule is one
 * nobody can audit. Section 2.4.3's decision chain records what the system advised alongside what
 * the operator decided; this is the first half of that pair.
 *
 * <p>The {@link Recommendation} is advisory only and drawn from section 6.3.4's closed vocabulary.
 * There is no engagement action here and no field one could be written into.
 *
 * @param recommendation what to advise
 * @param ruleId         the rule that fired
 * @param rationale      the operator-facing explanation
 */
public record AdvisoryRecommendation(Recommendation recommendation, String ruleId, String rationale) {

    public AdvisoryRecommendation {
        if (recommendation == null) {
            throw new IllegalArgumentException("An advisory must carry a recommendation");
        }
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException(
                    "An advisory must name the rule that produced it, so a past recommendation can"
                            + " be traced to the rule that made it");
        }
        rationale = rationale == null ? "" : rationale;
    }
}
