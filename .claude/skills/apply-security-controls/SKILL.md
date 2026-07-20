---
name: apply-security-controls
description: Implement platform security controls — JWT authentication, RBAC authorisation, tamper-evident audit logging, rate limiting, mTLS, secrets handling, input validation. Use when working on auth-service, the gateway's security, @PreAuthorize rules, the audit hash chain, or any security-sensitive path.
---

# Applying security controls

`docs/ARCHITECTURE.md` §7 governs. This is a defence-adjacent system; security is not a feature layer, it is a property of every path.

## Authentication (§7.3)

- Password hashing: **Argon2id** (m=64MB, t=3, p=4). Never bcrypt-only, never SHA.
- TOTP MFA: ±1 window, replay-blocked.
- Access token: **RS256, 15-minute TTL**, claims per §7.3 (roles + explicit `perms`).
- Refresh token: 7-day, **rotating**. Presenting a rotated token revokes the entire family and raises a security alert (OAuth 2.1 BCP).
- Storage: access token in JS memory only; refresh in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie. Never `localStorage`.
- Revocation: Redis denylist keyed by `jti`, TTL = remaining lifetime.
- The gateway validates tokens against cached JWKS — it does **not** call auth-service per request.

## Authorisation — RBAC (§7.4)

- Permissions are the atomic unit; roles are bags of permissions. Six roles: VIEWER, OPERATOR, ANALYST, ENGINEER, AUDITOR, ADMIN.
- Enforce at three layers: gateway pre-check (fast reject), service `@PreAuthorize` (**authoritative**), and DB row-level security where a row has an owner.
- Permission strings come from the §7.4 matrix **exactly**. Do not invent one; if a needed permission isn't in the matrix, stop and ask.
- **Separation of duty**, enforced in code, not just UI:
  - `ADMIN` must **not** hold `audit:export`. The one who alters permissions must not produce the authoritative record of having done so.
  - No user may grant themselves a permission they lack → `403 SELF_ESCALATION_DENIED`.
- UI gating is convenience only. Every check must exist server-side.

## Audit logging — tamper-evident (§7.5)

Each record is hash-chained to its predecessor:

```
record_hash = SHA256(prev_hash ‖ audit_id ‖ timestamp ‖ actor_id ‖ action
                     ‖ resource_type ‖ resource_id ‖ outcome ‖ canonical_json(details))
```

- The table is append-only at the **database** level: `REVOKE UPDATE, DELETE` on the app role, plus `DO INSTEAD NOTHING` rules. Not enforced by application code alone.
- Checkpoint every 10,000 records / 24h, signed with the platform Ed25519 key.
- **Audit these always:** every auth attempt, permission change, alert acknowledge/assign/resolve, incident transition, policy/config change (with before→after diff), AI model version change, evidence export, audit query, chain verification.
- **Fail closed:** if the audit write fails, the security-relevant operation does not happen. An action that cannot be recorded is an action that is refused. State this behaviour explicitly wherever it applies.
- A chain-break is the highest-severity condition in the system (`audit.chain.broken.v1`).

## Rate limiting (§7.8)

Token bucket in Redis, keyed by the most specific identity. Limits per §7.8 table. Every 429 carries `X-RateLimit-*` and `Retry-After`. Rejections on security endpoints (`/auth/login`, `/audit/export`) are audited.

## Transport & secrets (§7.2, §7.7)

- mTLS between services; the orchestrator's certificate is the only one authorised on AI services.
- TLS 1.3 AEAD suites only. Service certs have 24-hour TTL with auto-rotation.
- **No secret in source control, a container image, or a production env var.** Environment variables locally, Vault in production. `gitleaks` runs pre-commit and in CI; a hit fails the build.

## Input validation

Validate at the boundary (Bean Validation on DTOs). Never interpolate user input into a query — parameterised statements only. Never reflect raw input into an error message.

## Checklist

- [ ] Argon2id, RS256, rotating refresh with reuse detection
- [ ] `@PreAuthorize` on every mutating endpoint, permission from the §7.4 matrix
- [ ] Separation-of-duty constraints enforced in code
- [ ] Audit records hash-chained; table append-only at the DB
- [ ] Every mutation audited; audit failure fails the operation closed
- [ ] Rate limits applied; security rejections audited
- [ ] No secret anywhere in the repo or image
- [ ] All input validated and parameterised
