package pk.nust.cuas.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.auth.infrastructure.persistence.entity.CredentialEntity;

/** Credentials, looked up by username at login and by user id everywhere else. */
public interface CredentialRepository extends JpaRepository<CredentialEntity, UUID> {

    Optional<CredentialEntity> findByUsername(String username);
}
