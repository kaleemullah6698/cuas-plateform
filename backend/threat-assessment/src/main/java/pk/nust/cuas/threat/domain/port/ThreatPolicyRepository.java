package pk.nust.cuas.threat.domain.port;

import java.util.List;
import java.util.Optional;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;

/**
 * Persistence for scoring policies and their immutable versions (ARCHITECTURE.md section 4.1.7,
 * {@code threat_policies}, {@code policy_versions}, {@code rule_definitions}).
 *
 * <p>There is no {@code update}. A policy version is immutable once written, because every
 * assessment cites the version that produced it and a mutable version would make that citation
 * meaningless — a score from last month would silently re-describe itself under this month's
 * thresholds. {@link #publish} therefore writes a <em>new</em> version and points the policy at it.
 */
public interface ThreatPolicyRepository {

    /**
     * @return the version currently in force, or empty when none has been published — the state a
     *         deployment is in before its first startup completes
     */
    Optional<ThreatPolicy> active();

    /**
     * @param version the version label
     * @return that version exactly as it was written, or empty when unknown
     */
    Optional<ThreatPolicy> byVersion(String version);

    /**
     * Writes a new immutable version and makes it the active one.
     *
     * @param policy the new version
     * @return the stored policy
     */
    ThreatPolicy publish(ThreatPolicy policy);

    /**
     * @return every version of the active policy, newest effective first
     */
    List<ThreatPolicy> versions();
}
