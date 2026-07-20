package pk.nust.cuas.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One member of a rotating refresh-token family (ARCHITECTURE.md section 7.3).
 *
 * <p>A family represents a single login. Each refresh mints a new member and marks the presented
 * one used; presenting a used member means the token was captured and replayed, which revokes the
 * entire family. Storing only {@code tokenHash} means a database disclosure yields no usable
 * tokens.
 *
 * @param id            this member's identifier
 * @param familyId      the login this member belongs to
 * @param userId        the authenticated user
 * @param tokenHash     digest of the opaque token; the token itself is never stored
 * @param sessionId     the session this family backs
 * @param issuedAt      when it was minted
 * @param expiresAt     when it stops being valid
 * @param usedAt        when it was exchanged, or {@code null} if still current
 * @param revokedAt     when it was revoked, or {@code null}
 * @param revokedReason why it was revoked, or {@code null}
 */
public record RefreshToken(
        UUID id,
        UUID familyId,
        UUID userId,
        String tokenHash,
        UUID sessionId,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt,
        String revokedReason) {

    /** @return true when this member has already been exchanged */
    public boolean isUsed() {
        return usedAt != null;
    }

    /** @return true when this member has been revoked */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * @param now the current instant
     * @return true when the member may still be exchanged
     */
    public boolean isUsable(Instant now) {
        return !isUsed() && !isRevoked() && now.isBefore(expiresAt);
    }
}
