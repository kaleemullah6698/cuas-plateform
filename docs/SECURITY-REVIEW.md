# Security Hardening Review — §7 pass (M8.D)

A review of the platform against ARCHITECTURE.md §7, at the end of M8. Each control is marked
**Implemented** (present and exercised by tests), **Partial** (present but with a documented gap), or
**Deferred** (planned for a named later milestone). The review is grounded in the code that exists;
where a control could not be fully verified in this pass it is called out as a finding.

This is a design-and-implementation review, not a penetration test. It records what is in place and
what remains, so the remaining items are tracked rather than assumed.

---

## 7.2 Transport & secrets

| Control | Status | Where / note |
|---|---|---|
| No secret in source control, image, or prod env var | **Implemented** | Passwords come from env vars locally; `docker-compose.yml` reads `${POSTGRES_PASSWORD}`, `${GRAFANA_PASSWORD}` etc. No secret is committed. |
| `gitleaks` pre-commit + CI | **Implemented (CI)** | `service-ci.yml` runs `gitleaks/gitleaks-action@v2` with `fetch-depth: 0`, so history is scanned rather than only the tip, and a hit fails the build. **SR-1 closed at M12.** The pre-commit half is still not wired — see SR-5. |
| mTLS between services, 24 h certs, auto-rotation | **Deferred** | Inter-service gRPC (orchestrator, config `WatchConfig`) is authorised transport-level by design; the mTLS material and rotation are an ops/deployment concern (K8s overlay), not yet provisioned in the dev compose. **Finding SR-2.** |
| TLS 1.3 AEAD only | **Deferred** | Terminated at the gateway/ingress in production; not enforced in the dev topology. |

## 7.3 Authentication

| Control | Status | Where |
|---|---|---|
| Argon2id (m=64MB, t=3, p=4) | **Implemented** | `auth-service` `AuthenticationService` + `AuthConfig`. |
| TOTP MFA, ±1 window, replay-blocked | **Implemented** | `auth-service` `AuthenticationService` / `AuthController`. |
| Access token RS256, 15-min TTL, `perms` claim | **Implemented** | `AccessTokenIssuer` + `JwtKeyProvider`; every resource server validates via `JwtPermissionsConverter`, and the `perms` claim drives `@PreAuthorize`. |
| Rotating refresh, 7-day, reuse detection revokes family | **Implemented** | `AuthenticationService` refresh rotation; `SecurityBookkeeping`. |
| `jti` denylist in Redis, TTL = remaining lifetime | **Implemented** | `auth-service` revocation + `UserDeactivatedConsumer`. |
| Access token in JS memory, refresh in `HttpOnly`/`Secure`/`SameSite=Strict` cookie | **Deferred (M9)** | Frontend token storage lands with the M9 console. |

## 7.4 Authorisation — RBAC

| Control | Status | Where |
|---|---|---|
| Permissions the atomic unit; six roles | **Implemented** | §7.4 matrix; permission strings used verbatim across services. |
| Enforced at gateway pre-check + service `@PreAuthorize` (authoritative) | **Implemented** | 26 `@PreAuthorize` sites across services; gateway pre-check in `api-gateway`. |
| Permission strings from the matrix, never invented | **Implemented** | Two documented gaps were **flagged, not papered over** by inventing a permission: notification-service self-service and config-service reads both gate on authentication / `policy:read` rather than a fabricated `notification:*` / `config:read`. |
| Separation of duty in code (ADMIN ∌ `audit:export`; no self-escalation) | **Implemented** | `user-service` `RoleAssignmentPolicy`, `SelfEscalationDeniedException`, `SeparationOfDutyViolationException`. |
| DB row-level security where a row has an owner | **Partial** | Enforced in application logic (owner-scoped queries, e.g. notification/config self-service); Postgres RLS policies not yet added. **Finding SR-3.** |

## 7.5 Audit logging — tamper-evident

| Control | Status | Where |
|---|---|---|
| Hash-chained append-only log | **Implemented** | `audit-service` chain; `audit.chain.broken.v1` is the highest-severity event. |
| Append-only at the DB (`REVOKE UPDATE, DELETE`, `DO INSTEAD NOTHING`) | **Implemented** | audit-service migration. |
| Fail-closed: an unauditable security action does not happen | **Implemented** | alert-service incident changes fail closed on `AuditWriteException`; **config-service writes the before/after change-log row in the same transaction as the change** (M8.B), so a config change that cannot be recorded rolls back. |
| Chain verification API + signed checkpoints | **Implemented** | `audit-service` `AuditController` verify/checkpoints. |
| Chain-break alerting | **Implemented (M8.D)** | `AuditChainBroken` Prometheus rule fires on any `cuas_audit_chain_broken_total` increase. |

## 7.8 Rate limiting

| Control | Status | Where |
|---|---|---|
| Token bucket in Redis, keyed by most specific identity | **Implemented** | `api-gateway` `RateLimitFilter`. |
| Every 429 carries `X-RateLimit-*` + `Retry-After` | **Implemented** | `RateLimitFilter`. |
| Security-endpoint rejections audited | **Implemented** | `api-gateway` emits `platform.gateway.ratelimit.v1`, consumed by audit + reporting. |
| Observability of rejections | **Implemented (M8.D)** | Security dashboard panels for 429/403; `AuthFailureSpike` rule. |

## Input validation

| Control | Status | Where |
|---|---|---|
| Bean Validation at the boundary | **Implemented** | `@Valid` DTOs across every controller (config, reporting, notification, alert, …). |
| Parameterised queries only | **Implemented** | Spring Data / JPQL throughout; no string-interpolated SQL. The M8 additions (config canonical JSON, report projection) treat all input as data. |
| No raw input reflected into errors | **Implemented** | RFC 7807 problem details; the access-denied handler deliberately does not name the missing permission. |

## 7.9 Retention

| Control | Status | Where |
|---|---|---|
| Kafka per-topic retention | **Implemented** | `create-topics.sh` sets retention/cleanup per §5.2 (37 primary topics after M8.A). |
| Elasticsearch ILM policies | **Partial** | Index templates + ILM JSON scaffolded under `infrastructure/elasticsearch/`; reporting-service's ES mirror is deferred (M8.C ran lean on the reportdb projection), so ILM wiring lands with it. **Finding SR-4.** |
| Log retention | **Implemented (M8.D)** | Loki `retention_period: 168h`; Tempo `block_retention: 48h`; Prometheus 15 d. |

---

## Findings

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| ~~SR-1~~ | ~~Medium~~ | **Closed at M12.** `gitleaks` now gates the backend pipeline over full history, alongside an OWASP dependency check (blocking on push, advisory on PR) and CodeQL. | — |
| SR-2 | Medium | mTLS material + 24 h rotation not provisioned in the dev topology. | Provision workload certs in the K8s overlay (SPIFFE/cert-manager) before any non-dev deployment; the gRPC surfaces already assume transport-level auth. |
| SR-3 | Low | Owner-scoping is enforced in application code, not Postgres RLS. | Add RLS policies on owner-bearing tables as defence in depth; application scoping stays the primary control. |
| SR-4 | Low | ES ILM retention not yet active (reporting ES mirror deferred). | Wire the ILM policies when the reporting-service Elasticsearch adapter is promoted from the reportdb projection. |
| SR-5 | Low | The `gitleaks` **pre-commit** hook §7.7 asks for is still not wired; only CI scans. | Add the hook so a secret is caught before it reaches history at all. CI catching it means it was already pushed, and a committed secret must then be treated as compromised and rotated regardless of whether the build went red. |
| SR-6 | Low (by design) | The §6.7 conformance suites are `@pytest.mark.skip` scaffolds — but the deeper fact is that **there is no Track B service to test**. `ai-services/*/src/**` contains only empty `__init__.py` packages and the mock provider is `.gitkeep` files; no gRPC server or inference code exists in Python. | Not a defect to fix, and not closable by writing test bodies. This is M6's recorded decision: the boundary was built with the **Java** `MockInferenceProvider` inside ai-orchestrator (which *is* covered by that module's tests and was exercised live in M11), and the real services arrive later behind the same `InferenceProvider` port with no orchestration change (§6.1, §6.4). Promoting Track B is an architecture-amendment task, not a hardening one. `ai-service-ci.yml` is wired now so the suites gate the build the day an implementation lands. |
| SR-7 | Low | No JaCoCo plugin is configured, so §8.4.1's 80% coverage gate cannot run. | Wire the plugin and agree the threshold; the CI workflow deliberately omits the gate rather than run a step that would fail for want of an execution. |

## Verdict

The authentication, authorisation, audit and rate-limiting controls of §7 — the core of a
defence-adjacent system — are **implemented and enforced server-side**, with fail-closed auditing now
extended to configuration changes (M8.B) and chain-break/anomaly alerting added (M8.D). The open items
are transport hardening (mTLS provisioning) and retention wiring (ES ILM), both deployment concerns
tracked above rather than gaps in the application. No control was found to be bypassable in the code
reviewed, and the two places the RBAC matrix was silent were resolved by flagging, not by inventing a
permission — which keeps §0's scope discipline intact.
