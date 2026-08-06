package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Section 2.4.3's decision chain: what the system advised, what the operator decided, who authorised
 * it, and — stated rather than implied — that no engagement action was taken.
 *
 * <p><strong>{@code operatorDecision} may contradict {@code systemRecommended}, and the record
 * exists because it may.</strong> A system whose advice is always followed is one nobody is
 * supervising; the divergence, when it happens, is the most interesting line in the incident.
 *
 * @param systemRecommended what threat-assessment advised, or null when the incident was declared
 *                          without an alert behind it
 * @param operatorDecision  what the operator actually did
 * @param authorisedBy      the user id who authorised it
 * @param authorisedByName  their display name
 * @param authorisedAt      when
 * @param auditRef          reference into the tamper-evident chain (section 7.5)
 * @param engagementAction  always {@link EngagementAction#NONE} — the type has no other value
 */
public record DecisionChain(
        Recommendation systemRecommended,
        String operatorDecision,
        String authorisedBy,
        String authorisedByName,
        Instant authorisedAt,
        String auditRef,
        EngagementAction engagementAction) {

    public DecisionChain {
        Objects.requireNonNull(engagementAction, "engagementAction");
        operatorDecision = operatorDecision == null ? "" : operatorDecision;
        authorisedByName = authorisedByName == null ? "" : authorisedByName;
        auditRef = auditRef == null ? "" : auditRef;
    }

    /**
     * Records an operator decision.
     *
     * <p>The engagement action is supplied by this factory rather than by the caller. There is only
     * one legal value, and a parameter would invite a future caller to go looking for another.
     *
     * @param systemRecommended what was advised
     * @param operatorDecision  what was decided
     * @param authorisedBy      the authorising user id
     * @param authorisedByName  their display name
     * @param authorisedAt      when
     * @param auditRef          the audit chain reference
     * @return the decision chain
     */
    public static DecisionChain of(Recommendation systemRecommended, String operatorDecision,
                                   String authorisedBy, String authorisedByName,
                                   Instant authorisedAt, String auditRef) {
        return new DecisionChain(systemRecommended, operatorDecision, authorisedBy,
                authorisedByName, authorisedAt, auditRef, EngagementAction.NONE);
    }

    /** @return what the system advised, or empty when nothing did */
    public Optional<Recommendation> recommendation() {
        return Optional.ofNullable(systemRecommended);
    }

    /** @return {@code true} when the operator did something other than what was advised */
    public boolean divergedFromAdvice() {
        return systemRecommended != null
                && !operatorDecision.equalsIgnoreCase(systemRecommended.name());
    }
}
