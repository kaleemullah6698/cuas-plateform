package pk.nust.cuas.user.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.user.infrastructure.persistence.entity.RoleChangeLogEntity;

/** Spring Data access to the role change log. Insert and read only. */
public interface RoleChangeLogJpaRepository extends JpaRepository<RoleChangeLogEntity, Long> {

    List<RoleChangeLogEntity> findByUserIdOrderByOccurredAtDesc(UUID userId);
}
