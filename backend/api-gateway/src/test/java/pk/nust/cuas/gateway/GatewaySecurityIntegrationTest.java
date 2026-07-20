package pk.nust.cuas.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.gateway.infrastructure.cache.RedisTokenDenylist;
import pk.nust.cuas.gateway.infrastructure.messaging.producer.GatewayEventPublisher;
import pk.nust.cuas.gateway.support.StubUpstream;
import pk.nust.cuas.gateway.support.TestIdentityProvider;

/**
 * The gateway as a client meets it: a real servlet container, real signature verification against
 * a real JWKS endpoint, real Redis, and a real upstream behind it.
 *
 * <p>Everything asserted here is a claim ARCHITECTURE.md section 7.3 or 7.4 makes about the edge.
 * They are asserted end to end rather than per class because each of them is a property of the
 * assembled filter chain — the order of the filters is as much a part of the control as the
 * filters themselves, and only an assembled gateway can demonstrate it.
 *
 * <p>Kafka is mocked out: what the gateway publishes is telemetry, and standing up a broker to
 * assert an access-log record would slow every test here for no security claim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GatewaySecurityIntegrationTest {

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
        // One upstream standing in for user-service; the route table is configuration.
        registry.add("gateway.routes[0].id", () -> "user-service");
        registry.add("gateway.routes[0].uri", UPSTREAM::uri);
        registry.add("gateway.routes[0].prefixes[0]", () -> "/api/v1/users");
        registry.add("gateway.routes[1].id", () -> "auth-service");
        registry.add("gateway.routes[1].uri", UPSTREAM::uri);
        registry.add("gateway.routes[1].prefixes[0]", () -> "/api/v1/auth");
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
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private ResponseEntity<String> get(String path, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    // ── Authentication (section 7.3) ─────────────────────────────────────────────

    @Test
    void aValidTokenReachesTheUpstream() {
        var token = IDENTITY.validToken("usr-1", List.of("user:read"));

        ResponseEntity<String> response = get("/api/v1/users", token.bearer());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(UPSTREAM.received()).isTrue();
    }

    @Test
    void aRequestWithNoTokenIsRefusedAndNeverReachesTheUpstream() {
        ResponseEntity<String> response = get("/api/v1/users", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The point of an edge: an unauthenticated request costs the platform nothing behind it.
        assertThat(UPSTREAM.received()).isFalse();
    }

    @Test
    void aTokenSignedByAnUnpublishedKeyIsRefused() {
        var forged = IDENTITY.forgedToken("usr-1");

        assertThat(get("/api/v1/users", forged.bearer()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(UPSTREAM.received()).isFalse();
    }

    @Test
    void anExpiredTokenIsRefused() {
        assertThat(get("/api/v1/users", IDENTITY.expiredToken("usr-1").bearer()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenMintedForAnotherAudienceIsRefused() {
        // Same issuer, same signing key, wrong audience: without an audience check this would be
        // accepted, and a token for any future second API would work against this one.
        assertThat(get("/api/v1/users",
                IDENTITY.wrongAudienceToken("usr-1").bearer()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aStructurallyInvalidBearerValueIsRefusedRatherThanCrashing() {
        assertThat(get("/api/v1/users", "Bearer not-a-jwt").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aRevokedTokenIsRefusedImmediatelyRatherThanAtExpiry() {
        var token = IDENTITY.validToken("usr-1", List.of("user:read"));
        assertThat(get("/api/v1/users", token.bearer()).getStatusCode()).isEqualTo(HttpStatus.OK);

        redisTemplate.opsForValue().set(
                RedisTokenDenylist.KEY_PREFIX + token.jti(), "logout",
                java.time.Duration.ofMinutes(15));
        UPSTREAM.reset();

        // Section 7.3 promises immediate revocation. Without the denylist this token would keep
        // working for the remainder of its fifteen minutes.
        assertThat(get("/api/v1/users", token.bearer()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(UPSTREAM.received()).isFalse();
    }

    @Test
    void everyAuthenticationFailureLooksIdenticalToTheCaller() {
        var forged = get("/api/v1/users", IDENTITY.forgedToken("usr-1").bearer());
        var expired = get("/api/v1/users", IDENTITY.expiredToken("usr-1").bearer());
        var wrongAudience = get("/api/v1/users", IDENTITY.wrongAudienceToken("usr-1").bearer());

        // An attacker holding a token must not learn why it was refused. The correlation ID and
        // timestamp differ per request by design; nothing that describes the failure may.
        assertThat(List.of(forged, expired, wrongAudience)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(withoutPerRequestFields(response.getBody()))
                    .isEqualTo(withoutPerRequestFields(forged.getBody()));
        });
    }

    private static String withoutPerRequestFields(String problemJson) {
        return problemJson
                .replaceAll("\"timestamp\":\"[^\"]*\"", "")
                .replaceAll("\"correlationId\":\"[^\"]*\"", "");
    }

    // ── Authorisation pre-check (section 7.4) ────────────────────────────────────

    @Test
    void aCallerLackingTheRequiredPermissionIsRefusedAtTheEdge() {
        var viewer = IDENTITY.validToken("usr-2", List.of("track:read", "alert:read"));

        ResponseEntity<String> response = rest.exchange(url("/api/v1/users"), HttpMethod.POST,
                new HttpEntity<>("{}", jsonWith(viewer.bearer())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("user:write");
        assertThat(UPSTREAM.received()).isFalse();
    }

    @Test
    void aCallerHoldingThePermissionIsForwarded() {
        var admin = IDENTITY.validToken("usr-3", List.of("user:read", "user:write"));

        ResponseEntity<String> response = rest.exchange(url("/api/v1/users"), HttpMethod.POST,
                new HttpEntity<>("{}", jsonWith(admin.bearer())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(UPSTREAM.lastRequest("method")).isEqualTo("POST");
    }

    @Test
    void grantingARoleDemandsRoleWriteRatherThanUserWrite() {
        var userAdmin = IDENTITY.validToken("usr-4", List.of("user:read", "user:write"));

        ResponseEntity<String> response = rest.exchange(url("/api/v1/users/42/roles"),
                HttpMethod.POST, new HttpEntity<>("{}", jsonWith(userAdmin.bearer())), String.class);

        // Editing a profile must not imply handing out ADMIN.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("role:write");
    }

    @Test
    void anUnknownPermissionInATokenConfersNothing() {
        var token = IDENTITY.validToken("usr-5", List.of("user:write:all", "superuser"));

        ResponseEntity<String> response = rest.exchange(url("/api/v1/users"), HttpMethod.POST,
                new HttpEntity<>("{}", jsonWith(token.bearer())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aRouteThePreCheckHasNoRuleForIsStillAuthenticated() {
        // The pre-check may refuse but never grant. An unknown route is not an open one.
        assertThat(get("/api/v1/tracks", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Error contract and correlation ───────────────────────────────────────────

    @Test
    void rejectionsAreRfc7807WithACorrelationId() {
        ResponseEntity<String> response = get("/api/v1/users", null);

        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        assertThat(response.getBody())
                .contains("\"code\":\"unauthenticated\"")
                .contains("correlationId")
                .contains("https://cuas.nust.edu.pk/errors/");
        // No stack trace, no exception class, no internal detail.
        assertThat(response.getBody()).doesNotContain("Exception").doesNotContain("org.spring");
    }

    @Test
    void aSuppliedCorrelationIdIsEchoedAndCarriedToTheUpstream() {
        HttpHeaders headers = jsonWith(IDENTITY.validToken("usr-6", List.of("user:read")).bearer());
        headers.set(CorrelationId.HEADER, "corr-abc-123");

        ResponseEntity<String> response = rest.exchange(url("/api/v1/users"), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("corr-abc-123");
        // The same value on both legs is what makes a request traceable across services.
        assertThat(UPSTREAM.lastRequest("correlationId")).isEqualTo("corr-abc-123");
    }

    @Test
    void aCorrelationIdIsMintedWhenTheCallerSuppliesNone() {
        get("/api/v1/users", IDENTITY.validToken("usr-7", List.of("user:read")).bearer());

        assertThat(UPSTREAM.lastRequest("correlationId")).isNotBlank();
    }

    // ── Routing (section 4.1.1) ──────────────────────────────────────────────────

    @Test
    void thePathReachesTheUpstreamUnchanged() {
        get("/api/v1/users/42", IDENTITY.validToken("usr-8", List.of("user:read")).bearer());

        // Rewriting at the edge is the usual reason a path in an audit record cannot be found in
        // the owning service's logs.
        assertThat(UPSTREAM.lastRequest("path")).isEqualTo("/api/v1/users/42");
    }

    @Test
    void theAuthorizationHeaderIsPassedThroughSoTheServiceCanAuthoriseAuthoritatively() {
        var token = IDENTITY.validToken("usr-9", List.of("user:read"));
        get("/api/v1/users", token.bearer());

        // The gateway pre-check is not the decision; the service must be able to make its own.
        assertThat(UPSTREAM.lastRequest("authorization")).isEqualTo(token.bearer());
    }

    // ── Probes (section 4.1.1) ───────────────────────────────────────────────────

    @Test
    void probesAreReachableWithoutATokenAndReportStatus() {
        assertThat(get("/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/health", null).getBody()).contains("UP");
        assertThat(get("/health/ready", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders jsonWith(String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        return headers;
    }
}
