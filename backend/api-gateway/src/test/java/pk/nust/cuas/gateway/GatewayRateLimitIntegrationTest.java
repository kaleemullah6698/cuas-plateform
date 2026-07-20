package pk.nust.cuas.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.commons.audit.AuditActions;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.gateway.infrastructure.messaging.producer.GatewayEventPublisher;
import pk.nust.cuas.gateway.support.StubUpstream;
import pk.nust.cuas.gateway.support.TestIdentityProvider;
import pk.nust.cuas.gateway.v1.GatewayRateLimited;

/**
 * The section 7.8 limits as enforced by the assembled gateway against a real Redis.
 *
 * <p>The unit and adapter tests establish that the buckets are arithmetically correct. What is
 * asserted here is different and cannot be established anywhere else: that the right bucket is
 * chosen for the right request, that the refusal carries the headers section 7.8 mandates, and
 * that a refusal on a security endpoint reaches the audit log.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GatewayRateLimitIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static final TestIdentityProvider IDENTITY = new TestIdentityProvider();
    private static final StubUpstream UPSTREAM = new StubUpstream();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("gateway.auth.jwk-set-uri", IDENTITY::jwkSetUri);
        registry.add("gateway.auth.issuer", () -> TestIdentityProvider.ISSUER);
        registry.add("gateway.auth.audience", () -> TestIdentityProvider.AUDIENCE);
        registry.add("gateway.routes[0].id", () -> "auth-service");
        registry.add("gateway.routes[0].uri", UPSTREAM::uri);
        registry.add("gateway.routes[0].prefixes[0]", () -> "/api/v1/auth");
        registry.add("gateway.routes[1].id", () -> "user-service");
        registry.add("gateway.routes[1].uri", UPSTREAM::uri);
        registry.add("gateway.routes[1].prefixes[0]", () -> "/api/v1/users");
    }

    @AfterAll
    static void stopFixtures() {
        IDENTITY.close();
        UPSTREAM.close();
    }

    @MockitoBean
    private GatewayEventPublisher eventPublisher;

    @MockitoBean
    private AuditPublisher auditPublisher;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @LocalServerPort
    private int port;

    @BeforeEach
    void reset() {
        UPSTREAM.reset();
        // Buckets are keyed by address, and every test here shares 127.0.0.1.
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private ResponseEntity<String> login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"secret\"}";
        return rest.exchange("http://127.0.0.1:" + port + "/api/v1/auth/login",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void loginIsRefusedAfterTheBurstOfFivePerAddress() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(login("someone" + attempt).getStatusCode())
                    .as("attempt %d", attempt).isEqualTo(HttpStatus.OK);
        }

        // Different account each time: the address bucket is what stops a host from spraying.
        assertThat(login("someone6").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void aRefusalCarriesEveryHeaderSection78Requires() {
        for (int i = 0; i < 5; i++) {
            login("u" + i);
        }
        ResponseEntity<String> refused = login("u6");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        HttpHeaders headers = refused.getHeaders();
        assertThat(headers.getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(headers.getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(headers.getFirst("X-RateLimit-Reset")).isNotBlank();
        assertThat(Long.parseLong(headers.getFirst("Retry-After"))).isPositive();
    }

    @Test
    void quotaHeadersAreOnSuccessfulResponsesTooSoAClientCanPaceItself() {
        HttpHeaders headers = login("pacer").getHeaders();

        // A login draws on two buckets: 5/min per address and 10/hour per account. The headers
        // report the account bucket, whose burst of three leaves the least room and is therefore
        // the limit this caller meets first.
        assertThat(headers.getFirst("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(headers.getFirst("X-RateLimit-Remaining")).isEqualTo("2");
    }

    @Test
    void aRefusalIsRfc7807AndNamesTheRuleWithoutNamingTheAccount() {
        for (int i = 0; i < 5; i++) {
            login("u" + i);
        }
        ResponseEntity<String> refused = login("target.account");

        assertThat(refused.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        assertThat(refused.getBody()).contains("rate-limit-exceeded").contains("login-per-ip");
        // Echoing the identity would confirm to an unauthenticated caller that an account exists.
        assertThat(refused.getBody()).doesNotContain("target.account");
    }

    @Test
    void aRefusedRequestNeverReachesTheUpstream() {
        for (int i = 0; i < 5; i++) {
            login("u" + i);
        }
        UPSTREAM.reset();

        login("u6");

        assertThat(UPSTREAM.received()).isFalse();
    }

    @Test
    void aRefusalOnALoginIsAudited() {
        for (int i = 0; i < 5; i++) {
            login("u" + i);
        }
        login("u6");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditActions.RATE_LIMIT_EXCEEDED);
            assertThat(event.outcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(event.details()).containsEntry("rule", "login-per-ip");
        });
    }

    @Test
    void everyRefusalIsPublishedForAlertingRegardlessOfSampling() {
        for (int i = 0; i < 5; i++) {
            login("u" + i);
        }
        login("u6");

        ArgumentCaptor<GatewayRateLimited> captor =
                ArgumentCaptor.forClass(GatewayRateLimited.class);
        verify(eventPublisher, atLeastOnce()).publishRateLimited(captor.capture());
        assertThat(captor.getValue().getRule()).isEqualTo("login-per-ip");
        assertThat(captor.getValue().getRetryAfterSeconds()).isPositive();
    }

    @Test
    void theAccountLimitIsSeparateFromTheAddressLimit() {
        // The per-account bucket has a burst of three. Three attempts against one account exhaust
        // it while the address bucket, with a burst of five, still has room.
        for (int i = 0; i < 3; i++) {
            assertThat(login("victim").getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> refused = login("victim");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(refused.getBody()).contains("login-per-account");
    }

    @Test
    void aRefusedLoginStillHasItsBodyIntactForTheUpstreamWhenAllowed() {
        login("body.check");

        // The rate limiter reads the body to find the account. If it consumed the stream, the
        // login would arrive at auth-service empty and every login would fail.
        assertThat(UPSTREAM.lastRequest("body")).contains("\"username\":\"body.check\"");
    }

    @Test
    void anAuthenticatedRequestIsChargedToItsUserNotItsAddress() {
        var first = IDENTITY.validToken("usr-a", List.of("user:read"));
        var second = IDENTITY.validToken("usr-b", List.of("user:read"));

        // Both callers share 127.0.0.1. If the general quota were keyed by address they would
        // share one bucket, and one busy operator would throttle another.
        assertThat(callAsUser(first).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(callAsUser(second).getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("49");
    }

    @Test
    void loginRefusalsDoNotAffectAuthenticatedTraffic() {
        for (int i = 0; i < 6; i++) {
            login("u" + i);
        }

        assertThat(callAsUser(IDENTITY.validToken("usr-c", List.of("user:read")))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void probesAreNeverRateLimited() {
        for (int i = 0; i < 10; i++) {
            login("u" + i);
        }

        // An instance that is being probed must keep answering its probe while it refuses traffic,
        // or an orchestrator would restart a gateway that is working exactly as designed.
        ResponseEntity<String> health = rest.getForEntity(
                "http://127.0.0.1:" + port + "/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anAnonymousRequestToANonLoginRouteIsRejectedWithoutSpendingAnyQuota() {
        rest.getForEntity("http://127.0.0.1:" + port + "/api/v1/users", String.class);

        // It is refused by authentication, not by a quota; charging an address here would let one
        // host behind a NAT exhaust the quota of everyone behind it.
        verify(auditPublisher, never()).publish(any());
    }

    private ResponseEntity<String> callAsUser(TestIdentityProvider.Token token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, token.bearer());
        return rest.exchange("http://127.0.0.1:" + port + "/api/v1/users",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
