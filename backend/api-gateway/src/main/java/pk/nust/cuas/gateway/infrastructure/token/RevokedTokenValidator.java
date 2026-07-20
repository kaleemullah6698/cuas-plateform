package pk.nust.cuas.gateway.infrastructure.token;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import pk.nust.cuas.gateway.domain.service.TokenDenylist;

/**
 * Rejects an access token that has been revoked before its natural expiry (section 7.3).
 *
 * <p>A signature check alone cannot express revocation: the token remains cryptographically valid
 * for its full fifteen minutes no matter what happens to the session behind it. Without this
 * validator, "log out" and "revoke this user's access" would both mean "in up to fifteen minutes".
 *
 * <p>Runs as an {@link OAuth2TokenValidator} rather than as another filter so that every path that
 * decodes a token — today the gateway, tomorrow anything reusing this decoder — gets the check
 * without having to remember to ask for it.
 */
public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final String ERROR_CODE = "invalid_token";

    private final TokenDenylist denylist;

    public RevokedTokenValidator(TokenDenylist denylist) {
        this.denylist = denylist;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        // An unreachable denylist throws EdgeStateUnavailableException, which is rendered as 503.
        // Answering "valid" there would let a revoked token through for as long as Redis was down.
        if (denylist.isRevoked(token.getId())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE, "The token has been revoked.", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
