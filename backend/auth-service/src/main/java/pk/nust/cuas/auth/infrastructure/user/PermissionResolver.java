package pk.nust.cuas.auth.infrastructure.user;

import java.util.List;
import java.util.UUID;

/**
 * Resolves what a user may do, so the issued token can carry it.
 *
 * <p>A port rather than a direct gRPC call, for two reasons. It keeps the dependency direction
 * right — auth-service depends on an interface it owns, not on user-service's transport — and it
 * lets the authentication flows be tested without standing up a second service.
 */
public interface PermissionResolver {

    /**
     * @param userId the authenticated user
     * @return their roles, permissions, and whether the account is still active
     */
    Resolution resolve(UUID userId);

    /**
     * @param roles       role codes
     * @param permissions permission authority strings
     * @param active      false when the account is deactivated, in which case no token may issue
     */
    record Resolution(List<String> roles, List<String> permissions, boolean active) {
    }
}
