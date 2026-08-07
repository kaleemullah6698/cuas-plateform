package pk.nust.cuas.config.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.config.infrastructure.persistence.entity.ConfigChangeEntity;

/** Spring Data repository for {@code config_change_log}. */
public interface ConfigChangeJpaRepository extends JpaRepository<ConfigChangeEntity, Long> {
}
