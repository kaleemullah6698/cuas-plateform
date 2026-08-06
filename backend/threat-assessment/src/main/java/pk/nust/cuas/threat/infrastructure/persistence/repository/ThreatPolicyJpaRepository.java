package pk.nust.cuas.threat.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.threat.infrastructure.persistence.entity.ThreatPolicyEntity;

/** Spring Data repository for {@code threat_policies}. */
public interface ThreatPolicyJpaRepository extends JpaRepository<ThreatPolicyEntity, String> {
}
