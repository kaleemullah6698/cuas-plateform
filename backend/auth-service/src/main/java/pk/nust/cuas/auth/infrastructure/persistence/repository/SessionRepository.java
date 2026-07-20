package pk.nust.cuas.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.auth.infrastructure.persistence.entity.SessionEntity;

/** Authenticated sessions. */
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    /**
     * Revokes every live session of a user, for logout-all and deactivation.
     *
     * @param userId the user losing access
     * @param now    revocation instant
     * @return how many sessions were revoked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SessionEntity s SET s.revokedAt = :now
             WHERE s.userId = :userId AND s.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
