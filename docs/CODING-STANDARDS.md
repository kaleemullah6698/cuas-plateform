# Coding Standards

Binding for all code in this repository. Derived from `ARCHITECTURE.md` §4.3 and the project's mandated engineering practices.

---

## 1. Architectural rules

### 1.1 Dependency direction

Every backend service uses the Clean/hexagonal layout in ARCHITECTURE.md §4.3. Dependencies point **inward**, always:

```
api/  ──────────┐
                ├──▶  domain/   ◀── the centre. Depends on nothing.
infrastructure/ ┘
```

| Package | May import | May **not** import |
|---|---|---|
| `domain/` | JDK only | Spring, JPA, Kafka, gRPC, `api/`, `infrastructure/` |
| `api/` | `domain/`, Spring Web | `infrastructure/` |
| `infrastructure/` | `domain/`, frameworks | `api/` |
| `config/` | all | — |

**`domain/` must compile without Spring on the classpath.** That is the test. If a domain class carries `@Entity`, `@Component`, or `@Autowired`, the layering has been violated.

Repository *interfaces* are declared in `domain/`; their *implementations* live in `infrastructure/persistence/repository/`. The domain declares what it needs; infrastructure supplies it.

### 1.2 Domain and persistence models are separate

`domain/model/` holds aggregates, entities, and value objects — pure Java. `infrastructure/persistence/entity/` holds JPA entities, shaped by the database. `infrastructure/persistence/mapper/` translates between them.

This duplication is deliberate. It is what allows the schema to change without the domain changing, and the domain to be unit-tested without a database.

### 1.3 DTOs never leak

Domain models never cross the API boundary. `api/dto/` types are the wire contract; they are mapped explicitly. A controller returning a domain object is a defect.

### 1.4 Composition over inheritance

Inheritance is reserved for genuine substitutability. Shared behaviour is composed — inject a collaborator, do not extend a base class. Abstract "Base*Service" classes are not used in this codebase.

### 1.5 No `util` packages

A `util` package accumulates unrelated code and has no cohesion. Helpers belong with the concept they serve: geometry in `domain/policy/`, formatting in the adapter that formats, genuinely shared concerns in `platform-commons`.

---

## 2. Every REST endpoint

Non-negotiable checklist. An endpoint missing any of these is incomplete:

- [ ] **Validation** — Jakarta Bean Validation on the request DTO; `@Valid` at the parameter
- [ ] **Exception handling** — mapped through the service's `@RestControllerAdvice`; never a raw stack trace to the client
- [ ] **Logging** — entry at DEBUG, failure at WARN/ERROR, with the correlation ID
- [ ] **DTOs** — distinct request and response types; no domain models, no `Map<String, Object>`
- [ ] **OpenAPI** — `@Operation`, `@ApiResponse` for every status the endpoint returns
- [ ] **Authorisation** — `@PreAuthorize` with the permission from the §7.4 RBAC matrix

Error responses use RFC 7807 `application/problem+json`.

---

## 3. Java

- **Java 21.** Records for value objects and DTOs. Sealed interfaces for closed hierarchies. Pattern matching over instanceof chains.
- **Constructor injection only.** No field injection. A single constructor needs no `@Autowired`.
- **Immutability by default.** `final` fields; return unmodifiable collections; no setters on domain models.
- **`Optional` for absent return values.** Never for fields or parameters.
- **No `null` returns** from public methods. Return `Optional`, an empty collection, or throw.
- **Package-private by default.** Widen only when a caller outside the package genuinely needs it.
- **No checked exceptions** across architectural boundaries; translate to domain exceptions.

### Naming

| Kind | Convention | Example |
|---|---|---|
| Aggregate | Noun, no suffix | `Track`, `Incident` |
| Value object | Noun | `ThreatScore`, `GeoPosition` |
| Domain service | `<Concept>Service` | `CorrelationService` |
| Repository interface | `<Aggregate>Repository` | `TrackRepository` |
| JPA entity | `<Aggregate>Entity` | `TrackEntity` |
| Mapper | `<Aggregate>Mapper` | `TrackMapper` |
| DTO | `<Action><Noun>Request/Response` | `CreateIncidentRequest` |
| Domain event | Past tense | `TrackCreated`, `ThreatLevelChanged` |

---

## 4. TypeScript / React

- **`strict: true`.** No `any`. Use `unknown` and narrow.
- **Named exports** except for route components.
- **Function components** with typed props. No `React.FC`.
- **Four state domains, four tools** (ARCHITECTURE.md §3.4): TanStack Query for server state, custom external stores for streaming state, Zustand for UI state, React Hook Form for form state. Do not conflate them.
- **Streaming data never passes through React state per-message.** It lives in an external store, coalesced to `requestAnimationFrame`. This is the single most important frontend performance rule (§3.4).
- **Import boundaries**, enforced by ESLint `import/no-restricted-paths`:
  - `design-system/` must not import from `features/`
  - `features/*` must not import from each other — communicate via `store/` or the router
- **Accessibility is not optional.** WCAG 2.1 AA per §2.1.7. Threat state is never encoded by colour alone.

---

## 5. Testing

| Layer | Tool | Requirement |
|---|---|---|
| Domain | JUnit 5, plain | No Spring context. Fast. Covers every business rule. |
| Adapter | `@WebMvcTest`, `@DataJpaTest` | Sliced, not full-context |
| Integration | Testcontainers | Real Postgres and Kafka. No mocked infrastructure. |
| Contract (AI) | pytest | The shared conformance suite. Identical for every AI service and the mock. |
| E2E | Playwright | Critical operator flows |

Coverage gate: 80% line coverage on changed code. Coverage is a floor, not a goal — an untested business rule is a defect regardless of the percentage.

Tests are named for behaviour: `rejectsDetectionWithFutureTimestamp`, not `testValidate2`.

---

## 6. Commits

Every commit leaves the codebase in a working state: it compiles, tests pass, the application starts.

Conventional Commits:

```
feat(track-correlation): add Kalman state estimation
fix(auth-service): reject refresh token reuse
docs(architecture): add section 11 repository strategy
```

Scope is the module name.

---

## 7. Security

Per ARCHITECTURE.md §7:

- **No secret in source control**, in a container image, or in a production environment variable. `gitleaks` runs pre-commit and in CI.
- **Every mutation is audited.** An operator action that cannot be recorded does not happen — security-relevant operations fail closed if the audit log is unavailable (§7.5).
- **AI services have no database credentials and no network egress.** They are pure functions (§7.1).
- **Permissions are enforced server-side.** UI gating is a convenience, never a control.
- **No engagement code paths.** The recommendation vocabulary is closed (§6.3.4). No output value encodes an RF-active action.
