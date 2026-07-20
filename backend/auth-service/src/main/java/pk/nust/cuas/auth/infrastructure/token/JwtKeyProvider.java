package pk.nust.cuas.auth.infrastructure.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Supplies the RSA key pair that signs access tokens, and publishes its public half as JWKS
 * (ARCHITECTURE.md sections 7.3, 7.7).
 *
 * <p>RS256 rather than a symmetric algorithm because the gateway and every service must verify
 * tokens without holding the ability to mint them. With HS256 any verifier could forge a token for
 * any user.
 *
 * <p>The private key is supplied as base64 PKCS#8 through configuration, which in production comes
 * from the secrets manager (section 7.7). When none is configured an ephemeral key is generated so
 * a developer can run the service without ceremony — and that fact is logged as a warning, because
 * an ephemeral key silently invalidates every token on restart and must never be what production
 * ends up running.
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);
    private static final int KEY_SIZE = 2048;

    private final RSAKey rsaKey;

    public JwtKeyProvider(@Value("${auth.jwt.private-key:}") String configuredPrivateKey,
                          @Value("${auth.jwt.key-id:}") String configuredKeyId) {
        String keyId = configuredKeyId == null || configuredKeyId.isBlank()
                ? UUID.randomUUID().toString()
                : configuredKeyId;

        if (configuredPrivateKey == null || configuredPrivateKey.isBlank()) {
            log.warn("No signing key configured; generating an ephemeral RSA key. "
                    + "Every token becomes invalid when this process restarts, and other "
                    + "instances will not verify these tokens. Configure auth.jwt.private-key "
                    + "from the secrets manager outside development.");
            this.rsaKey = generateEphemeral(keyId);
        } else {
            this.rsaKey = load(configuredPrivateKey, keyId);
            log.info("Loaded configured RSA signing key, kid={}", keyId);
        }
    }

    private RSAKey generateEphemeral(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(keyId)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate an RSA signing key", ex);
        }
    }

    private RSAKey load(String base64Pkcs8, String keyId) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Pkcs8.replaceAll("\\s", ""));
            var spec = new java.security.spec.PKCS8EncodedKeySpec(der);
            var factory = java.security.KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(spec);

            // Derive the public key from the private one so only a single secret is configured;
            // a separately supplied public key could drift out of step with it.
            var privateCrt = (java.security.interfaces.RSAPrivateCrtKey) privateKey;
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new java.security.spec.RSAPublicKeySpec(
                            privateCrt.getModulus(), privateCrt.getPublicExponent()));

            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "auth.jwt.private-key is not a valid base64 PKCS#8 RSA private key", ex);
        }
    }

    /** @return the signing key, including its private half */
    public RSAKey signingKey() {
        return rsaKey;
    }

    /**
     * @return the public JWKS document verifiers fetch; contains no private material
     */
    public JWKSet publicJwkSet() {
        return new JWKSet(rsaKey.toPublicJWK());
    }
}
