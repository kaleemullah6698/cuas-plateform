package pk.nust.cuas.threat.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.threat.infrastructure.persistence.entity.PolicyVersionEntity;

/** Spring Data repository for {@code policy_versions}. */
public interface PolicyVersionJpaRepository extends JpaRepository<PolicyVersionEntity, Long> {

    Optional<PolicyVersionEntity> findByPolicyIdAndVersion(String policyId, String version);

    Optional<PolicyVersionEntity> findByVersion(String version);

    List<PolicyVersionEntity> findByPolicyIdOrderByEffectiveAtDesc(String policyId);
}
