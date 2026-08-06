package pk.nust.cuas.alert.domain.model;

/**
 * The advisory recommendation carried on an alert (ARCHITECTURE.md section 6.3.4).
 *
 * <p><strong>Closed by specification, and closed in code by being an enum with no extension
 * point.</strong> This service does not produce recommendations — it relays what threat-assessment
 * advised, so the operator sees the advice beside the alert it justified. It is declared here rather
 * than imported from the contract because {@code domain/} compiles without Protobuf (CLAUDE.md
 * section 2), and it is closed here for the same reason it is closed there: no value encodes an
 * engagement action, and none may be added without an architecture amendment.
 *
 * <p>{@code RecommendationVocabularyTest} fails the build if a sixth value appears, and asserts that
 * this enum matches threat-assessment's exactly — two copies of a closed vocabulary that could drift
 * would be worse than one.
 */
public enum Recommendation {

    MONITOR,
    TRACK_AND_MONITOR,
    REQUEST_VISUAL_ID,
    ESCALATE_TO_SUPERVISOR,
    RECOMMEND_OPERATOR_REVIEW
}
