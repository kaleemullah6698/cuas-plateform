package pk.nust.cuas.config.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.config.infrastructure.persistence.entity.ConfigurationEntity;

/** Spring Data repository for {@code configurations}. */
public interface ConfigurationJpaRepository extends JpaRepository<ConfigurationEntity, String> {

    List<ConfigurationEntity> findByKindIn(Collection<String> kinds);
}
