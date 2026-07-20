package pk.nust.cuas.gateway.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.gateway.domain.model.RateLimitKey;
import pk.nust.cuas.gateway.domain.model.RateLimitRule;

/**
 * The section 7.8 table, read as buckets.
 */
class RateLimitPolicyTest {

    private final RateLimitPolicy policy = new RateLimitPolicy();

    private static List<String> ruleNames(List<RateLimitKey> buckets) {
        return buckets.stream().map(bucket -> bucket.rule().name()).toList();
    }

    @Test
    void aLoginIsChargedToBothItsAddressAndItsAccount() {
        List<RateLimitKey> buckets = policy.bucketsFor(new RateLimitPolicy.LimitedRequest(
                "POST", "/api/v1/auth/login", "10.0.0.9", null, "k.ullah"));

        // Either alone is bypassable: one host against many accounts, or many hosts against one.
        assertThat(ruleNames(buckets)).containsExactly("login-per-ip", "login-per-account");
        assertThat(buckets.get(0).identity()).isEqualTo("10.0.0.9");
        assertThat(buckets.get(1).identity()).isEqualTo("k.ullah");
    }

    @Test
    void aLoginWithNoReadableAccountStillPaysTheAddressLimit() {
        List<RateLimitKey> buckets = policy.bucketsFor(new RateLimitPolicy.LimitedRequest(
                "POST", "/api/v1/auth/login", "10.0.0.9", null, null));

        assertThat(ruleNames(buckets)).containsExactly("login-per-ip");
    }

    @Test
    void anAnonymousRequestToAnythingElseDrawsOnNoBucket() {
        // It is rejected by authentication, not by a quota, and charging an address for it would
        // let one host behind a NAT exhaust the quota of everyone behind it.
        assertThat(policy.bucketsFor(new RateLimitPolicy.LimitedRequest(
                "GET", "/api/v1/users", "10.0.0.9", null, null))).isEmpty();
    }

    @Test
    void anAuthenticatedRequestIsChargedToItsUser() {
        List<RateLimitKey> buckets = policy.bucketsFor(new RateLimitPolicy.LimitedRequest(
                "GET", "/api/v1/users", "10.0.0.9", "usr-1", null));

        assertThat(ruleNames(buckets)).containsExactly("authenticated-api-per-user");
        assertThat(buckets.get(0).identity()).isEqualTo("usr-1");
    }

    @Test
    void anAuditExportIsChargedToItsOwnQuotaBeforeTheGeneralOne() {
        List<RateLimitKey> buckets = policy.bucketsFor(new RateLimitPolicy.LimitedRequest(
                "POST", "/api/v1/audit/export", "10.0.0.9", "usr-1", null));

        // Specific first, so a request refused by the narrow quota has not already spent a token
        // from the broad one.
        assertThat(ruleNames(buckets))
                .containsExactly("audit-export-per-user", "authenticated-api-per-user");
    }

    @Test
    void reportGenerationHasItsOwnQuotaToo() {
        assertThat(ruleNames(policy.bucketsFor(new RateLimitPolicy.LimitedRequest(
                "POST", "/api/v1/reports/generate", "10.0.0.9", "usr-1", null))))
                .containsExactly("report-generate-per-user", "authenticated-api-per-user");
    }

    @Test
    void securitySensitiveRulesAreTheOnesSection78SaysToAudit() {
        assertThat(RateLimitPolicy.LOGIN_PER_IP.securitySensitive()).isTrue();
        assertThat(RateLimitPolicy.LOGIN_PER_ACCOUNT.securitySensitive()).isTrue();
        assertThat(RateLimitPolicy.AUDIT_EXPORT_PER_USER.securitySensitive()).isTrue();
        assertThat(RateLimitPolicy.AUTHENTICATED_API_PER_USER.securitySensitive()).isFalse();
    }

    @Test
    void everyRuleMatchesItsRowInTheTable() {
        assertRow(RateLimitPolicy.LOGIN_PER_IP, 5, Duration.ofMinutes(1), 5);
        assertRow(RateLimitPolicy.LOGIN_PER_ACCOUNT, 10, Duration.ofHours(1), 3);
        assertRow(RateLimitPolicy.AUTHENTICATED_API_PER_USER, 300, Duration.ofMinutes(1), 50);
        assertRow(RateLimitPolicy.REPORT_GENERATE_PER_USER, 10, Duration.ofHours(1), 2);
        assertRow(RateLimitPolicy.AUDIT_EXPORT_PER_USER, 5, Duration.ofHours(1), 1);
    }

    private void assertRow(RateLimitRule rule, long limit, Duration window, long burst) {
        assertThat(rule.limit()).isEqualTo(limit);
        assertThat(rule.window()).isEqualTo(window);
        assertThat(rule.burst()).isEqualTo(burst);
    }

    @Test
    void bucketsOfDifferentRulesNeverCollideForTheSameIdentity() {
        String identity = "usr-1";
        String general = new RateLimitKey(
                RateLimitPolicy.AUTHENTICATED_API_PER_USER, identity).storageKey();
        String export = new RateLimitKey(
                RateLimitPolicy.AUDIT_EXPORT_PER_USER, identity).storageKey();

        assertThat(general).isNotEqualTo(export);
    }
}
