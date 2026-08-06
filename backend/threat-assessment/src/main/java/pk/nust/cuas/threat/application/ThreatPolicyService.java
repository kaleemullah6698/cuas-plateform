package pk.nust.cuas.threat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.threat.domain.exception.InvalidPolicyException;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;
import pk.nust.cuas.threat.domain.port.ThreatPolicyRepository;
import pk.nust.cuas.threat.domain.service.ThreatScorer;

/**
 * Reads, publishes and simulates scoring policies (ARCHITECTURE.md section 4.1.7).
 *
 * <p>Publishing a policy changes what the system concludes about every track in the airspace, which
 * makes it one of the most consequential mutations the platform offers. It is therefore
 * <strong>audited fail-closed</strong> (section 7.5): the audit record is written inside the
 * transaction and before the new version becomes active, so a policy change that could not be
 * recorded does not happen at all. That ordering is the whole control — an audit written afterwards,
 * or asynchronously, records changes that succeeded and misses exactly the ones worth investigating.
 */
@Service
public class ThreatPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ThreatPolicyService.class);

    /** Audit action for a published policy version. Domain-specific, per {@code AuditActions}. */
    public static final String POLICY_PUBLISHED = "THREAT_POLICY_PUBLISHED";

    private static final String RESOURCE_TYPE = "THREAT_POLICY";

    private final ThreatPolicyRepository policies;
    private final ActivePolicySupplier activePolicy;
    private final ThreatScorer scorer;
    private final AuditPublisher audit;
    private final Clock clock;

    public ThreatPolicyService(ThreatPolicyRepository policies,
                              ActivePolicySupplier activePolicy,
                              ThreatScorer scorer,
                              AuditPublisher audit,
                              Clock clock) {
        this.policies = policies;
        this.activePolicy = activePolicy;
        this.scorer = scorer;
        this.audit = audit;
        this.clock = clock;
    }

    /** @return the policy currently in force */
    @Transactional(readOnly = true)
    public ThreatPolicy active() {
        return activePolicy.active();
    }

    /** @return every version of the active policy, newest effective first */
    @Transactional(readOnly = true)
    public List<ThreatPolicy> versions() {
        return policies.versions();
    }

    /**
     * Publishes a new immutable policy version and makes it active.
     *
     * @param policy        the new version, already validated by its own constructor
     * @param actorId       the authenticated user making the change
     * @param justification why — required, and recorded in the audit detail
     * @return the published version
     * @throws InvalidPolicyException when the version label duplicates an existing one
     * @throws pk.nust.cuas.commons.audit.AuditWriteException when the change cannot be recorded, in
     *         which case the transaction rolls back and the policy does not change
     */
    @Transactional
    public ThreatPolicy publish(ThreatPolicy policy, String actorId, String justification) {
        if (policies.byVersion(policy.version()).isPresent()) {
            throw new InvalidPolicyException(
                    "Version '" + policy.version() + "' already exists. A published version is"
                            + " immutable, because every assessment cites the version that produced"
                            + " it; reusing a label would silently re-describe past scores.");
        }

        ThreatPolicy previous = activePolicy.active();
        audit.publish(AuditEvent.of(POLICY_PUBLISHED, RESOURCE_TYPE)
                .auditId(UUID.randomUUID())
                .occurredAt(Instant.now(clock))
                .actor(actorId, ActorType.USER)
                .resourceId(policy.policyId())
                .outcome(AuditOutcome.SUCCESS)
                .correlationId(CorrelationId.currentOrNew())
                .details(Map.of(
                        "previousVersion", previous.version(),
                        "newVersion", policy.version(),
                        "justification", justification,
                        "thresholds", previous.thresholds() + " -> " + policy.thresholds(),
                        "weights", previous.weights() + " -> " + policy.weights()))
                .build());

        ThreatPolicy published = policies.publish(policy);
        activePolicy.refresh(published);
        log.info("Policy {} published version {} by {}",
                published.policyId(), published.version(), actorId);
        return published;
    }

    /**
     * Scores hypothetical evidence without persisting or publishing anything — the {@code
     * POST /threats/evaluate} what-if of section 4.1.7.
     *
     * <p>The point of the endpoint is to answer "what would this policy do?" before it is imposed on
     * live traffic, so the result deliberately never reaches {@code threat.assessed.v1}: a
     * hypothetical that raised a real alert would be worse than no what-if at all.
     *
     * @param evidence the hypothetical evidence
     * @param version  the policy version to evaluate against, or empty for the active one
     * @return the assessment that would have been produced
     * @throws InvalidPolicyException when the named version does not exist
     */
    @Transactional(readOnly = true)
    public ThreatAssessment evaluate(TrackEvidence evidence, java.util.Optional<String> version) {
        ThreatPolicy policy = version
                .map(v -> policies.byVersion(v).orElseThrow(() -> new InvalidPolicyException(
                        "No policy version '" + v + "' exists.")))
                .orElseGet(activePolicy::active);
        UUID hypotheticalTrack = evidence.snapshot()
                .map(s -> s.trackId())
                .orElseGet(() -> new UUID(0L, 0L));
        return scorer.score(hypotheticalTrack, evidence, policy, Instant.now(clock));
    }
}
