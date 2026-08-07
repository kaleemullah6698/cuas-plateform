package pk.nust.cuas.config.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.config.infrastructure.persistence.entity.ConfigVersionEntity;

/** Spring Data repository for {@code config_versions}. */
public interface ConfigVersionJpaRepository extends JpaRepository<ConfigVersionEntity, Long> {

    List<ConfigVersionEntity> findByNamespaceOrderByVersionDesc(String namespace, Pageable page);

    Optional<ConfigVersionEntity> findByNamespaceAndVersion(String namespace, long version);
}
