package pk.nust.cuas.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to create an account. A new account is created with no roles; authority is granted
 * afterwards through the assignment endpoint, which enforces the section 7.4 checks.
 */
@Schema(description = "Create a user account")
public record CreateUserRequest(
        @Schema(example = "k.ullah")
        @NotBlank @Size(min = 3, max = 64)
        @Pattern(regexp = "^[a-z0-9._-]+$",
                message = "must contain only lowercase letters, digits, dot, underscore or hyphen")
        String username,

        @Schema(example = "k.ullah@nust.edu.pk")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(example = "Kaleem Ullah")
        @NotBlank @Size(max = 128) String displayName) {
}
