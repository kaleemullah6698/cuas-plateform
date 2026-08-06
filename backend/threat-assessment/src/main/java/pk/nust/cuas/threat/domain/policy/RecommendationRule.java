package pk.nust.cuas.threat.domain.policy;

import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatLevel;

/**
 * One rule of the deterministic recommendation engine (ARCHITECTURE.md section 6.3.4,
 * {@code rule_definitions}).
 *
 * <p>Rules are <em>data</em>, stored in {@code threatdb} and versioned with the policy that contains
 * them, which is what lets a deployment change what the system advises without a redeploy. They are
 * evaluated in priority order and the first match wins, so precedence is explicit rather than
 * emergent from the order rows happen to come back in.
 *
 * <p><strong>What a rule may produce is drawn from a closed enum.</strong> There is no expression
 * language, no scripting hook, no free-text recommendation field, and no condition beyond the five
 * {@link RuleContext} carries. That constraint is the reason rules can safely be data at all:
 * configuration able to introduce a new kind of instruction would move the platform's advisory-only
 * scope limit into a database table, where no code review would ever see it again.
 *
 * @param ruleId                  stable identifier, recorded on every assessment this rule produced
 *                                so a past recommendation traces back to the rule that made it
 * @param priority                evaluation order; lower is evaluated first
 * @param minLevel                fires only at or above this threat level
 * @param requireClosing          when true, fires only for a track closing on a protected asset
 * @param reliability             whether the rule depends on the score having met the policy floor
 * @param requireUnclassified     when true, fires only while no AI service has produced a label —
 *                                the condition that motivates asking for a visual identification
 * @param minConsecutiveClosing   fires only once the track has closed for this many consecutive
 *                                updates; 0 imposes no constraint
 * @param recommendation          what to advise, from the closed vocabulary
 * @param rationale               operator-facing explanation of why this rule fired
 */
public record RecommendationRule(
        String ruleId,
        int priority,
        ThreatLevel minLevel,
        boolean requireClosing,
        ReliabilityCondition reliability,
        boolean requireUnclassified,
        int minConsecutiveClosing,
        Recommendation recommendation,
        String rationale) {

    public RecommendationRule {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("A rule must have an id — it is recorded on every"
                    + " assessment it produces, so an anonymous rule is an untraceable one");
        }
        if (minLevel == null || recommendation == null || reliability == null) {
            throw new IllegalArgumentException(
                    "A rule needs a minimum level, a reliability condition and a recommendation");
        }
        if (minConsecutiveClosing < 0) {
            throw new IllegalArgumentException(
                    "Minimum consecutive closing count cannot be negative, was " + minConsecutiveClosing);
        }
        rationale = rationale == null ? "" : rationale;
    }

    /**
     * @param context the facts to evaluate against
     * @return {@code true} when every condition of this rule holds
     */
    public boolean matches(RuleContext context) {
        return context.level().isAtLeast(minLevel)
                && (!requireClosing || context.closing())
                && reliability.isSatisfiedBy(context.isReliable())
                && (!requireUnclassified || !context.isClassified())
                && context.consecutiveClosingCount() >= minConsecutiveClosing;
    }
}
