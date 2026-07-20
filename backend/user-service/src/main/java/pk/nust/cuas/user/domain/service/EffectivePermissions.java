package pk.nust.cuas.user.domain.service;

import java.util.Set;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.user.domain.model.User;

/**
 * Resolves what a user may actually do (ARCHITECTURE.md section 7.4).
 *
 * <p>A deactivated account resolves to <em>no</em> permissions regardless of the roles still
 * recorded against it. Returning its stored roles would let a caller mint a token for a
 * deactivated user, so the status check belongs here, at the single point every caller goes
 * through, rather than being repeated at each call site.
 */
public final class EffectivePermissions {

    /**
     * @param user the user to resolve
     * @return the permissions the user may exercise right now; empty when deactivated
     */
    public Set<Permission> resolve(User user) {
        return user.isActive() ? user.effectivePermissions() : Set.of();
    }
}
