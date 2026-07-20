---
name: write-tests
description: Write tests at any layer of the platform — domain unit tests, sliced Spring tests, Testcontainers integration tests, AI contract conformance, and frontend component and E2E tests. Use when adding or improving test coverage for a service, endpoint, consumer, or UI feature.
---

# Writing tests

`docs/ARCHITECTURE.md` §11 (testing strategy) and `docs/CODING-STANDARDS.md` §5 govern.

## The layers, and what each is for

| Layer | Tool | Rule |
|---|---|---|
| Domain | JUnit 5, plain | **No Spring context, no database.** Fast. Covers every business rule and invariant. |
| Adapter | `@WebMvcTest`, `@DataJpaTest` | Sliced, not full-context. One adapter at a time. |
| Integration | Testcontainers | **Real** PostgreSQL and Kafka. Never an in-memory substitute. |
| AI contract | pytest | The shared conformance suite — identical for every AI service and the mock. |
| Frontend component | Vitest + Testing Library | Behaviour, not implementation. |
| E2E | Playwright | Critical operator flows against the running system. |

## Domain tests — the backbone

The domain compiles without Spring, so it tests without Spring. These are the fastest and most valuable tests in the codebase.

```java
class TrackTest {
    @Test
    void rejectsDetectionWithFutureTimestamp() {
        var future = Instant.now().plus(Duration.ofHours(1));
        assertThatThrownBy(() -> new Detection(SENSOR_ID, future))
            .isInstanceOf(InvalidDetectionException.class);
    }
}
```

Test the rule, not the getter. An invariant with no test is a defect regardless of coverage percentage.

## Integration tests — real infrastructure

```java
@SpringBootTest
@Testcontainers
class TrackPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void savesAndRetrievesTrackThroughRealSchema() { /* ... */ }
}
```

A test that mocks the repository is not an integration test. If it doesn't exercise the real driver, migration, and query, it proves nothing about persistence.

## Consumer tests — idempotency

Every Kafka consumer must have a test proving it is idempotent: deliver the same message twice, assert one effect. Delivery is at-least-once in most stages (§5.7); a consumer that isn't idempotent is a latent duplicate-alert bug.

## Endpoint tests — the three cases

Every endpoint gets at least: success, validation failure (400), and forbidden (403). RBAC is a security control; an untested `@PreAuthorize` is an unverified control.

```java
@WebMvcTest(IncidentController.class)
class IncidentControllerTest {
    @Test @WithMockUser(authorities = "incident:create")
    void createsIncident() { /* 201 */ }

    @Test @WithMockUser(authorities = "track:read")   // wrong permission
    void rejectsCallerWithoutPermission() { /* 403 */ }
}
```

## AI conformance

`ai-services/*/tests/test_contract.py` is one file, run against every service and the mock. Fill in the scaffolded bodies; do not fork it per service. Passing it is the definition of integrable (§6.7).

## Frontend

- Component tests assert what the operator sees and can do, not internal state.
- Streaming: test that the external store coalesces — N deltas produce ≤1 render per frame (§3.4).
- Accessibility: `axe-core` in the component test run; threat state carries shape + label, not colour alone.
- E2E covers the flows in §2.3: acknowledge a critical alert, declare an incident, navigate the map by keyboard.

Develop and test against `src/mocks/` scenarios — no backend required.

## Coverage

80% line coverage on changed code is the CI floor. It is a floor, not a target: full coverage of trivial getters while a business rule goes untested is worse than the number suggests. Test behaviour.

## Checklist

- [ ] Every business rule has a plain-JUnit domain test
- [ ] Persistence tested with Testcontainers, not mocks
- [ ] Every consumer has an idempotency test
- [ ] Every endpoint has success / 400 / 403
- [ ] AI services pass the shared conformance suite
- [ ] Frontend tests assert behaviour; `axe-core` runs
- [ ] Tests named for behaviour, not `test1`
