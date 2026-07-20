package pk.nust.cuas.gateway.infrastructure.token;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires the {@code aud} claim of section 7.3 to name this API.
 *
 * <p>Signature and issuer checks alone would accept any token the platform's own issuer had ever
 * minted, including one issued for a different audience. Checking the audience is what stops a
 * token meant for some future second API from being replayed against this one.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "The token was not issued for this API.", null));
    }
}
