package pk.nust.cuas.gateway.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * A stand-in for auth-service: an RSA key, a JWKS endpoint, and a token mint.
 *
 * <p>The gateway is tested against real signature verification rather than a stubbed decoder. A
 * mocked {@code JwtDecoder} would let every one of these tests pass on a gateway that accepted
 * unsigned tokens, which is exactly the defect worth catching. Claims mirror
 * {@code AccessTokenIssuer} of section 7.3.
 */
public final class TestIdentityProvider implements AutoCloseable {

    /** Matches the issuer configured in auth-service. */
    public static final String ISSUER = "https://cuas.nust.edu.pk/auth";

    /** Matches the audience configured in auth-service. */
    public static final String AUDIENCE = "cuas-api";

    private final RSAKey signingKey;
    private final RSAKey foreignKey;
    private final HttpServer server;

    public TestIdentityProvider() {
        this.signingKey = generate("kid-primary");
        this.foreignKey = generate("kid-foreign");
        try {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the test JWKS server", ex);
        }
        server.createContext("/auth/jwks", exchange -> {
            byte[] body = new JWKSet(signingKey.toPublicJWK())
                    .toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private static RSAKey generate(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(keyId)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate a test RSA key", ex);
        }
    }

    /** @return the URI the gateway should fetch the key set from */
    public String jwkSetUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/jwks";
    }

    /**
     * Mints a well-formed token.
     *
     * @param subject     the user id
     * @param permissions permission authorities for the {@code perms} claim
     * @return a signed compact JWT and its {@code jti}
     */
    public Token validToken(String subject, List<String> permissions) {
        return token(builder(subject, permissions).build(), signingKey);
    }

    /**
     * Mints a token signed by a key the gateway's JWKS does not publish.
     *
     * @param subject the user id
     * @return the forged token
     */
    public Token forgedToken(String subject) {
        return token(builder(subject, List.of("user:read")).build(), foreignKey);
    }

    /**
     * @param subject the user id
     * @return a token whose lifetime has already elapsed
     */
    public Token expiredToken(String subject) {
        Instant issued = Instant.now().minus(Duration.ofHours(2));
        return token(builder(subject, List.of("user:read"))
                .issueTime(Date.from(issued))
                .expirationTime(Date.from(issued.plus(Duration.ofMinutes(15))))
                .build(), signingKey);
    }

    /**
     * @param subject the user id
     * @return a token minted for a different audience
     */
    public Token wrongAudienceToken(String subject) {
        return token(builder(subject, List.of("user:read"))
                .audience("some-other-api")
                .build(), signingKey);
    }

    private JWTClaimsSet.Builder builder(String subject, List<String> permissions) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                .jwtID(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim("roles", List.of("OPERATOR"))
                .claim("perms", permissions)
                .claim("amr", List.of("pwd", "otp"))
                .claim("clearance", "UNCLASSIFIED");
    }

    private Token token(JWTClaimsSet claims, RSAKey key) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
            return new Token(jwt.serialize(), claims.getJWTID());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign a test token", ex);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * @param value the compact JWT
     * @param jti   the token identifier, for denylist tests
     */
    public record Token(String value, String jti) {

        /** @return the value prefixed for an {@code Authorization} header */
        public String bearer() {
            return "Bearer " + value;
        }
    }
}
