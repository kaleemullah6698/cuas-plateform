package pk.nust.cuas.auth.domain.model;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 time-based one-time passwords (ARCHITECTURE.md section 7.3).
 *
 * <p>Implemented directly rather than pulled from a library: the algorithm is small, and a
 * dependency here would sit on the authentication path where every added component is added
 * attack surface.
 *
 * <p>Verification accepts the adjacent time steps (±1, i.e. ±30 seconds) to tolerate clock skew
 * between the authenticator and the server, as section 7.3 specifies. Widening that window trades
 * security for convenience: each extra step is another code an attacker may guess.
 *
 * <p><strong>Replay.</strong> A code stays valid for its whole step, so accepting it twice would
 * let an observer reuse it. {@link #verify} therefore reports which step matched, and the caller
 * records it; a step at or below the last accepted one must be refused.
 */
public final class TotpGenerator {

    /** Seconds per time step (RFC 6238 default). */
    public static final int STEP_SECONDS = 30;

    /** Digits in a generated code. */
    public static final int DIGITS = 6;

    /** Steps of clock skew tolerated on each side. */
    public static final int SKEW_STEPS = 1;

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int SECRET_BYTES = 20;
    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};

    private final SecureRandom random = new SecureRandom();

    /**
     * @return a new base64 shared secret, 160 bits as RFC 4226 recommends for HMAC-SHA1
     */
    public String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    /**
     * @param secret base64 shared secret
     * @param at     the instant to generate for
     * @return the six-digit code for that instant's time step
     */
    public String generate(String secret, Instant at) {
        return generateForStep(secret, stepOf(at));
    }

    /**
     * Verifies a code against the accepted window, rejecting replays.
     *
     * @param secret        base64 shared secret
     * @param code          the presented code
     * @param at            now
     * @param lastUsedStep  highest step already accepted for this secret, or {@code null} if none
     * @return the matched step when the code is valid and not a replay, otherwise empty
     */
    public java.util.Optional<Long> verify(String secret, String code, Instant at,
                                           Long lastUsedStep) {
        if (code == null || code.length() != DIGITS) {
            return java.util.Optional.empty();
        }
        long current = stepOf(at);
        for (long step = current - SKEW_STEPS; step <= current + SKEW_STEPS; step++) {
            // A step already used cannot be accepted again, even though its code is still within
            // the validity window — that is exactly the replay this check exists to stop.
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            if (constantTimeEquals(generateForStep(secret, step), code)) {
                return java.util.Optional.of(step);
            }
        }
        return java.util.Optional.empty();
    }

    private long stepOf(Instant at) {
        return at.getEpochSecond() / STEP_SECONDS;
    }

    private String generateForStep(String secret, long step) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());

            // RFC 4226 dynamic truncation.
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            // Zero-padded: a code with a leading zero is still six digits.
            return String.format("%0" + DIGITS + "d", binary % POWERS[DIGITS]);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute a TOTP code", ex);
        }
    }

    /**
     * Compares in time independent of how many characters match, so response timing cannot be used
     * to discover a valid code digit by digit.
     */
    private boolean constantTimeEquals(String expected, String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= expected.charAt(i) ^ actual.charAt(i);
        }
        return difference == 0;
    }
}
