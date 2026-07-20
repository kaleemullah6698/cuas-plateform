package pk.nust.cuas.auth.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.auth.infrastructure.persistence.entity.MfaSecretEntity;

/** TOTP shared secrets and the highest time step already accepted. */
public interface MfaSecretRepository extends JpaRepository<MfaSecretEntity, UUID> {
}
