package pk.nust.cuas.auth.infrastructure.token;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mints RS256 access tokens carrying the claims of ARCHITECTURE.md section 7.3.
 *
 * <p>The permission list is resolved once, here, and embedded as {@code perms}. That is what lets
 * every downstream service authorise from the token alone rather than calling user-service on each
 * request — and it is why the token life is short: an embedded permission set is a snapshot, and
 * fifteen minutes bounds how stale it can be.
 */
@Component
public class AccessTokenIssuer {

    private final JwtKeyProvider keyProvider;
    private final String issuer;
    private final String audience;
    private final Duration tokenTtl;

    public AccessTokenIssuer(JwtKeyProvider keyProvider,
                             @Value("${auth.jwt.issuer:https://cuas.nust.edu.pk/auth}") String issuer,
                             @Value("${auth.jwt.audience:cuas-api}") String audience,
                             @Value("${auth.jwt.access-token-ttl:PT15M}") Duration tokenTtl) {
        this.keyProvider = keyProvider;
        this.issuer = issuer;
        this.audience = audience;
        this.tokenTtl = tokenTtl;
    }

    /**
     * @param userId      the authenticated user, which becomes the subject
     * @param sessionId   the session this token belongs to
     * @param roles       role codes, for display and coarse checks
     * @param permissions resolved permission authorities, the authoritative grant
     * @param issuedAt    when the token is issued
     * @return the signed compact JWT
     */
    public IssuedToken issue(UUID userId, UUID sessionId, List<String> roles,
                             List<String> permissions, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(tokenTtl);
        String tokenId = UUID.randomUUID().toString();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(tokenId)
                .claim("sid", sessionId.toString())
                .claim("roles", roles)
                .claim("perms", permissions)
                // amr records how the caller authenticated, so a reviewer can tell a
                // password-only session from one that cleared the second factor.
                .claim("amr", List.of("pwd", "otp"))
                .claim("clearance", "UNCLASSIFIED")
                .build();

        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(keyProvider.signingKey().getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(keyProvider.signingKey().toRSAPrivateKey()));
            return new IssuedToken(jwt.serialize(), tokenId, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign the access token", ex);
        }
    }

    /**
     * @param value     the compact JWT
     * @param tokenId   the {@code jti}, used for revocation
     * @param expiresAt when it stops being valid
     */
    public record IssuedToken(String value, String tokenId, Instant expiresAt) {
    }
}
