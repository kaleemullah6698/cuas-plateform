package pk.nust.cuas.threat.domain.model;

/**
 * The advisory recommendation vocabulary (ARCHITECTURE.md section 6.3.4).
 *
 * <p><strong>Closed by specification, and closed in code by being an enum with no extension
 * point.</strong> This platform produces advisory recommendations only; every recommendation
 * terminates at an operator authorisation gate, and no member here encodes an engagement action. RF
 * jamming, GNSS spoofing and cyber takeover are outside the software boundary.
 *
 * <p>There is deliberately no {@code CUSTOM} member, no free-text carrier, and no interface a caller
 * could implement to introduce a sixth value: the type itself is the guarantee. Extending it
 * requires an architecture amendment and supervisor approval, not a pull request — and
 * {@code RecommendationVocabularyTest} fails the build if a member is added, so the constraint holds
 * against an edit that forgets to read this comment.
 */
public enum Recommendation {

    /** Keep the track under routine observation. The default when no rule fires. */
    MONITOR,

    /** Watch this track specifically; it is behaving in a way that warrants attention. */
    TRACK_AND_MONITOR,

    /** Ask for optical confirmation before drawing a conclusion. */
    REQUEST_VISUAL_ID,

    /** Put this in front of a supervisor. */
    ESCALATE_TO_SUPERVISOR,

    /** The evidence is too weak or too partial for the system to advise; a person should look. */
    RECOMMEND_OPERATOR_REVIEW
}
