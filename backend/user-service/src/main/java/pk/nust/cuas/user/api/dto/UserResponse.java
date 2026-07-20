package pk.nust.cuas.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.user.domain.model.User;

/** A user account as returned by the API. */
@Schema(description = "A platform user account")
public record UserResponse(
        @Schema(description = "Account identifier") UUID id,
        @Schema(description = "Login name") String username,
        @Schema(description = "Contact address") String email,
        @Schema(description = "Name shown in the console") String displayName,
        @Schema(description = "ACTIVE | DEACTIVATED") String status,
        @Schema(description = "Assigned roles") List<String> roles,
        @Schema(description = "Effective permissions, the union of the roles") List<String> permissions,
        @Schema(description = "When the account was created") Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(), user.username(), user.email(), user.displayName(),
                user.status().name(),
                user.roles().stream().map(Enum::name).sorted().toList(),
                user.effectivePermissions().stream().map(Permission::authority).sorted().toList(),
                user.createdAt());
    }
}
