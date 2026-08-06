package pk.nust.cuas.threat.domain.policy;

import pk.nust.cuas.threat.domain.model.ThreatLevel;

/**
 * The facts a {@link RecommendationRule} is evaluated against.
 *
 * <p>A small, closed set. Every condition a rule can express is a field here, which is what keeps
 * the rules genuinely declarative: there is no expression to parse, no reflection over the
 * assessment, and no way for a rule stored in the database to reach a fact this record does not
 * carry. Widening what rules can see is a code change under review — the alternative, a general
 * expression language over the whole assessment, would put the platform's advisory logic somewhere
 * no code review ever looks.
 *
 * @param level                   the assessed threat level
 * @param closing                 whether the track is closing on a protected asset
 * @param isReliable              whether the score met the policy's reliability floor
 * @param isClassified            whether any AI service has produced a label for this track
 * @param consecutiveClosingCount how many consecutive updates have shown the track closing
 */
public record RuleContext(
        ThreatLevel level,
        boolean closing,
        boolean isReliable,
        boolean isClassified,
        int consecutiveClosingCount) {

    public RuleContext {
        if (level == null) {
            throw new IllegalArgumentException("A rule is evaluated against an assessed level");
        }
    }
}
