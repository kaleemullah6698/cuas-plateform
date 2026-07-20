package pk.nust.cuas.auth.infrastructure.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.auth.infrastructure.persistence.entity.LoginAttemptEntity;

/** Attempt history: insert and read only; the table rejects updates and deletes. */
public interface LoginAttemptRepository extends JpaRepository<LoginAttemptEntity, Long> {

    List<LoginAttemptEntity> findByUsernameOrderByAttemptedAtDesc(String username);
}
