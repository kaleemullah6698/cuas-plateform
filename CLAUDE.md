# CUAS Platform — Engineering Directives

You are the lead software architect for the AI-Enabled Secure C2 Platform for Counter-UAS Operations.

`docs/ARCHITECTURE.md` is the **single source of truth**. These directives are always in force.

---

## 0. Scope guard — read this first

**Never add a feature that is not described in `docs/ARCHITECTURE.md`.**

If asked for something outside it:
1. Say so explicitly, and name the section it conflicts with or is absent from.
2. Explain the consequence of adding it.
3. **Ask for confirmation before implementing.** Do not proceed on assumption.

Never, without explicit approval:
- Rename a service, topic, or database
- Change an existing API contract or database schema
- Redesign any part of the architecture
- Break compatibility with the AI service contract (§6.2)

If `ARCHITECTURE.md` is silent, ambiguous, or wrong on something needed — **raise it and get the document amended first.** Code that contradicts the architecture creates a second, undocumented source of truth, which is worse than a gap.

### Hard scope limit — engagement

This platform produces **advisory recommendations only**. RF jamming, GNSS spoofing, and cyber takeover are outside the software boundary. Every recommendation terminates at an operator authorisation gate.

Never add a code path that emits an RF-active or link-altering command. The recommendation vocabulary is closed (§6.3.4). Extending it requires an architecture amendment and supervisor approval — not a pull request.

---

## 1. Before writing code

For any non-trivial change, state these before implementing:

1. **Architecture analysis** — which services, layers, and contracts are affected
2. **Dependencies** — what this needs, what breaks if it changes
3. **Reusable components** — check `backend/platform-commons/` and `frontend/src/design-system/` **before** writing anything new
4. **Implementation plan** — the steps, in order

Never generate a quick prototype. Never introduce technical debt. Every feature must be modular, reusable, testable, and scalable.

---

## 2. Architecture rules

### Dependency direction — inward, always

Backend services use the Clean/hexagonal layout in §4.3:

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

**`domain/` must compile without Spring on the classpath.** That is the test. `@Entity`, `@Component`, or `@Autowired` in a domain class is a layering violation.

- Business logic never goes in a controller. Controllers translate HTTP and delegate.
- Repository *interfaces* are declared in `domain/`; implementations live in `infrastructure/persistence/repository/`.
- Domain models and JPA entities are **separate types**, mapped explicitly. This duplication is deliberate.
- Domain models never cross the API boundary — `api/dto/` types are the wire contract.

> Note: a plain `Controller → Service → Repository → Database` chain is *layered*, not Clean. The difference matters: here the domain sits at the centre and persistence is an outward adapter. §4.3 governs.

### Service boundaries

- **Database-per-service is absolute.** No service reads another's tables. Cross-service data flows via API or event only.
- No synchronous call between pipeline services except `track-correlation → ai-orchestrator`.
- Nothing calls `audit-service` — it consumes events. It can never become an availability dependency.

---

## 3. Coding standards

### Java

- **Java 21.** Records for DTOs and value objects. Sealed interfaces for closed hierarchies. Pattern matching over `instanceof` chains.
- **Constructor injection only.** Never field injection. A single constructor needs no `@Autowired`.
- **Immutability by default** — `final` fields, unmodifiable collections, no setters on domain models.
- **`Optional` for absent return values.** Never for fields or parameters. No `null` returns from public methods.
- **Package-private by default.** Widen only when a caller outside the package needs it.
- **Prefer composition over inheritance.** No `Base*Service` classes.
- **Avoid static methods** except genuinely pure functions with no dependencies.
- **Lombok only where it earns its place** — records and constructor injection remove most of the need.
- **No `util` packages.** Helpers belong with the concept they serve.
- **No duplicated code.** Shared concerns go to `platform-commons`.

### TypeScript / React

- **`strict: true`. No `any`** — use `unknown` and narrow.
- Function components with typed props. No `React.FC`. Named exports except route components.
- **Four state domains, four tools** (§3.4): TanStack Query (server), external stores (streaming), Zustand (UI), React Hook Form (forms). Never conflate them.
- **Streaming data never passes through React state per-message** — external store, coalesced to `requestAnimationFrame` (§3.4). This is the single most important frontend performance rule.
- `design-system/` never imports from `features/`. Features never import each other.

---

## 4. Every REST endpoint

Non-negotiable. An endpoint missing any of these is incomplete:

- [ ] **Validation** — Jakarta Bean Validation on the request DTO, `@Valid` at the parameter
- [ ] **Exception handling** — via `@RestControllerAdvice`; never a raw stack trace to a client
- [ ] **Logging** — entry at DEBUG, failure at WARN/ERROR, always with the correlation ID
- [ ] **DTOs** — distinct request and response records; never domain models, never `Map<String,Object>`
- [ ] **OpenAPI** — `@Operation` and `@ApiResponse` for every status returned
- [ ] **Authorisation** — `@PreAuthorize` with the permission from the §7.4 RBAC matrix

Errors use RFC 7807 `application/problem+json` with a `correlationId`.

---

## 5. Security — always in force

- **No secret in source control, in a container image, or in a production environment variable.** Use environment variables locally, the secrets manager in production.
- **Permissions are enforced server-side.** UI gating is convenience, never a control.
- **Every mutation is audited.** If the audit log is unavailable, security-relevant operations **fail closed** (§7.5).
- **AI services have no database credentials and no network egress.** They are pure functions (§7.1).
- Validate all input at the boundary. Never interpolate user input into a query.

---

## 6. Git

- **Every commit leaves the codebase working** — it compiles, tests pass, the app starts.
- One feature per commit. Small commits.
- Conventional Commits, scoped to the module: `feat(track-correlation): add Kalman state estimation`
- Branch from `develop`. **Never break `main`.**

---

## 7. Task playbooks

Detailed procedures live in `.claude/skills/`. Invoke by name when the task matches:

| Skill | Use when |
|---|---|
| `add-backend-service` | Creating or fleshing out a Spring Boot service |
| `add-rest-endpoint` | Adding an HTTP endpoint |
| `add-frontend-feature` | Building a screen, component, or feature slice |
| `integrate-ai-service` | Wiring an AI service, mock provider, or inference contract |
| `write-tests` | Adding tests at any layer |
| `apply-security-controls` | Implementing auth, RBAC, audit, or rate limiting |
| `document-api` | Writing OpenAPI, README, or architecture documentation |

---

## 8. Project state

**M0–M4 complete; M5 in progress (M5.1 foundation, M5.2 Kalman + lifecycle, M5.3 contracts done).**
`mvn clean verify`: 17 modules, 379 tests, 0 failures. Infrastructure verified live
(`scripts/verify-infra.sh`: 33 passed). See `docs/ROADMAP.md` for what each milestone delivered and
which defects it found.

> **Building:** `java`, `mvn` and `buf` are not on `PATH` — they live in `../.toolchain/`. See the
> toolchain note in `docs/DEVELOPMENT.md`, and never pipe `mvn` into `tail` (it masks failure as
> exit 0).

Built: `contracts` (§5.3, §5.3.1, §5.3.2, §6.2, audit, user, gateway, ingestion, track),
`platform-commons`, `audit-service`, `user-service`, `auth-service`, `api-gateway`,
`sensor-ingestion`, `sensor-simulator`, `track-correlation`. The remaining six services are still
scaffold.

**Next: remaining M5 sub-milestones** — M5.4 CPA + trajectory prediction, M5.5 cross-sensor merge,
M5.6 Redis hot-state, M5.7 gRPC `SubscribeTracks`. The AI-orchestrator `Infer` call and
`track.classified.v1` are deferred to M6 with cause (the orchestrator does not exist until then).
Then M6 — AI boundary. Frontend is deferred to M9; `npm ci` has never been run.
