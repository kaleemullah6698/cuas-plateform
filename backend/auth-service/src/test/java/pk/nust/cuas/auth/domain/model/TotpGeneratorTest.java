package pk.nust.cuas.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * TOTP behaviour that matters for security: the skew window is exactly ±1 step, and a code cannot
 * be replayed inside its own validity window.
 */
class TotpGeneratorTest {

    private final TotpGenerator totp = new TotpGenerator();
    private final String secret = totp.generateSecret();
    private final Instant now = Instant.parse("2026-07-20T10:00:00Z");

    @Test
    void generatedSecretsAreDistinct() {
        assertThat(totp.generateSecret()).isNotEqualTo(totp.generateSecret());
    }

    @Test
    void generatedCodeIsSixDigits() {
        assertThat(totp.generate(secret, now)).hasSize(6).containsOnlyDigits();
    }

    @Test
    void codeIsStableWithinItsTimeStep() {
        // Same 30-second step, so the same code — otherwise a user typing it would always be late.
        assertThat(totp.generate(secret, now))
                .isEqualTo(totp.generate(secret, now.plusSeconds(29)));
    }

    @Test
    void codeChangesBetweenSteps() {
        assertThat(totp.generate(secret, now))
                .isNotEqualTo(totp.generate(secret, now.plusSeconds(30)));
    }

    @Test
    void currentCodeVerifies() {
        assertThat(totp.verify(secret, totp.generate(secret, now), now, null)).isPresent();
    }

    @Test
    void previousAndNextStepsAreAcceptedForClockSkew() {
        String previous = totp.generate(secret, now.minusSeconds(30));
        String next = totp.generate(secret, now.plusSeconds(30));

        assertThat(totp.verify(secret, previous, now, null)).isPresent();
        assertThat(totp.verify(secret, next, now, null)).isPresent();
    }

    @Test
    void twoStepsAwayIsRejected() {
        // The window is deliberately narrow: each extra accepted step is another guessable code.
        String tooOld = totp.generate(secret, now.minus(Duration.ofSeconds(60)));

        assertThat(totp.verify(secret, tooOld, now, null)).isEmpty();
    }

    @Test
    void aCodeCannotBeReplayedWithinItsOwnWindow() {
        String code = totp.generate(secret, now);
        long usedStep = totp.verify(secret, code, now, null).orElseThrow();

        // Same code, still inside its 30-second validity, but the step has been recorded.
        assertThat(totp.verify(secret, code, now.plusSeconds(5), usedStep)).isEmpty();
    }

    @Test
    void aLaterCodeStillVerifiesAfterAnEarlierOneWasUsed() {
        long usedStep = totp.verify(secret, totp.generate(secret, now), now, null).orElseThrow();
        Instant later = now.plusSeconds(30);

        assertThat(totp.verify(secret, totp.generate(secret, later), later, usedStep)).isPresent();
    }

    @Test
    void wrongCodeIsRejected() {
        assertThat(totp.verify(secret, "000000", now, null)).isEmpty();
    }

    @Test
    void malformedCodesAreRejectedWithoutError() {
        assertThat(totp.verify(secret, null, now, null)).isEmpty();
        assertThat(totp.verify(secret, "12345", now, null)).isEmpty();
        assertThat(totp.verify(secret, "abcdefg", now, null)).isEmpty();
    }

    @Test
    void aDifferentSecretDoesNotVerify() {
        String otherSecret = totp.generateSecret();

        assertThat(totp.verify(otherSecret, totp.generate(secret, now), now, null)).isEmpty();
    }
}
