package pk.nust.cuas.gateway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimitRuleTest {

    @Test
    void refillRateIsFractionalSoSlowQuotasAreNotRoundedAway() {
        // 5 per hour is one token every 720 seconds. Rounded to an integer tokens-per-second this
        // would be zero and the bucket would never refill.
        var rule = new RateLimitRule("audit-export", 5, Duration.ofHours(1), 1, true);

        assertThat(rule.refillTokensPerSecond()).isEqualTo(5.0d / 3600.0d);
    }

    @Test
    void refillRateOfTheGeneralApiQuotaIsFiveTokensASecond() {
        var rule = new RateLimitRule("api", 300, Duration.ofMinutes(1), 50, false);

        assertThat(rule.refillTokensPerSecond()).isEqualTo(5.0d);
    }

    @Test
    void degenerateRulesAreRejectedAtConstruction() {
        assertThatThrownBy(() -> new RateLimitRule("x", 0, Duration.ofMinutes(1), 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule("x", 1, Duration.ofMinutes(1), 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule("x", 1, Duration.ZERO, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule(" ", 1, Duration.ofMinutes(1), 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryAfterIsNeverZeroBecauseThatInvitesAnImmediateRetry() {
        Instant now = Instant.parse("2026-07-20T10:00:00Z");
        var decision = new RateLimitDecision(false, 5, 0, now.plusMillis(200));

        assertThat(decision.retryAfterSeconds(now)).isEqualTo(1L);
    }

    @Test
    void retryAfterReportsWholeSecondsUntilTheNextToken() {
        Instant now = Instant.parse("2026-07-20T10:00:00Z");
        var decision = new RateLimitDecision(false, 5, 0, now.plusSeconds(42));

        assertThat(decision.retryAfterSeconds(now)).isEqualTo(42L);
    }

    @Test
    void aBucketKeyIsNamespacedByItsRule() {
        var rule = new RateLimitRule("login-per-ip", 5, Duration.ofMinutes(1), 5, true);

        assertThat(new RateLimitKey(rule, "10.0.0.9").storageKey())
                .isEqualTo("cuas:ratelimit:login-per-ip:10.0.0.9");
    }

    @Test
    void aBlankIdentityIsRejectedSoUnrelatedCallersNeverShareABucket() {
        var rule = new RateLimitRule("login-per-ip", 5, Duration.ofMinutes(1), 5, true);

        assertThatThrownBy(() -> new RateLimitKey(rule, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
