package pk.nust.cuas.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Request to update the mutable profile fields. Username is immutable by design. */
@Schema(description = "Update a user profile")
public record UpdateUserRequest(
        @Schema(description = "New contact address") @Email @Size(max = 254) String email,
        @Schema(description = "New display name") @Size(max = 128) String displayName) {
}
