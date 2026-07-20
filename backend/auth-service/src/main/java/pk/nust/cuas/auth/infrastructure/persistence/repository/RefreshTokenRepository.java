package pk.nust.cuas.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.auth.infrastructure.persistence.entity.RefreshTokenEntity;

/** Members of rotating refresh-token families (ARCHITECTURE.md section 7.3). */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);

    /**
     * Revokes every unrevoked member of a family in one statement.
     *
     * <p>Used when reuse is detected: the compromised family must close atomically, and loading
     * each member to revoke it individually would widen the window in which the captured token
     * still works.
     *
     * @param familyId the family to close
     * @param now      revocation instant
     * @param reason   why it was revoked
     * @return how many members were revoked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshTokenEntity t
               SET t.revokedAt = :now, t.revokedReason = :reason
             WHERE t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("now") Instant now,
                     @Param("reason") String reason);

    /**
     * Revokes every family belonging to a user, for logout-all and deactivation.
     *
     * @param userId the user losing access
     * @param now    revocation instant
     * @param reason why
     * @return how many members were revoked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshTokenEntity t
               SET t.revokedAt = :now, t.revokedReason = :reason
             WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("now") Instant now,
                         @Param("reason") String reason);
}
