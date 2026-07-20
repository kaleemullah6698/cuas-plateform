package pk.nust.cuas.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pk.nust.cuas.commons.security.Role;

/**
 * Request to grant or revoke a role.
 *
 * <p>The justification is mandatory: section 7.4 requires a role change to be justified and the
 * justification recorded before the change commits. Making it a required field means an
 * unexplained privilege change cannot be expressed by the API at all.
 */
@Schema(description = "Grant or revoke a role, with the required justification")
public record RoleChangeRequest(
        @Schema(description = "The role", example = "OPERATOR") @NotNull Role role,
        @Schema(description = "Why this change is being made")
        @NotBlank @Size(min = 10, max = 1000) String justification) {
}
