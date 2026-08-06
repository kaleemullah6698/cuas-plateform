package pk.nust.cuas.threat.domain.service;

import pk.nust.cuas.threat.domain.model.AdvisoryRecommendation;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.policy.RecommendationRule;
import pk.nust.cuas.threat.domain.policy.RuleContext;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * Turns an assessed level into advice by evaluating the policy's rules in priority order
 * (ARCHITECTURE.md section 6.3.4).
 *
 * <p>First match wins. Rules are already sorted by {@link ThreatPolicy}, so precedence is a property
 * of the data rather than of the order rows happened to be read in.
 *
 * <p><strong>There is always a recommendation.</strong> When no rule matches — a policy with a gap
 * in it — the engine falls back to {@link Recommendation#RECOMMEND_OPERATOR_REVIEW} rather than
 * returning nothing. A track on the operator's screen with no advice attached is worse than an
 * explicit "a person should look at this": silence is indistinguishable from a system that has
 * judged the track harmless, and the fallback says plainly that the policy did not cover this case.
 * Falling back to {@code MONITOR} would have been the quieter choice and the wrong one — it would
 * report a policy gap as an all-clear.
 */
public class RecommendationEngine {

    /** Rule id recorded when no configured rule matched. */
    public static final String FALLBACK_RULE_ID = "no-matching-rule";

    /**
     * @param context the facts to evaluate
     * @param policy  the policy whose rules to apply
     * @return the advice, never null
     */
    public AdvisoryRecommendation advise(RuleContext context, ThreatPolicy policy) {
        return policy.rules().stream()
                .filter(rule -> rule.matches(context))
                .findFirst()
                .map(RecommendationEngine::toAdvisory)
                .orElseGet(RecommendationEngine::fallback);
    }

    private static AdvisoryRecommendation toAdvisory(RecommendationRule rule) {
        return new AdvisoryRecommendation(rule.recommendation(), rule.ruleId(), rule.rationale());
    }

    private static AdvisoryRecommendation fallback() {
        return new AdvisoryRecommendation(
                Recommendation.RECOMMEND_OPERATOR_REVIEW,
                FALLBACK_RULE_ID,
                "No policy rule matched this track's assessment; a person should review it.");
    }
}
