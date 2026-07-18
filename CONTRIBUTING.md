# Contributing

## Before writing code

`docs/ARCHITECTURE.md` is the single source of truth. Do not make architectural decisions independently of it, and do not add features it does not describe.

If the document is silent, ambiguous, or wrong on something you need — **raise it and get the document amended first.** Code that contradicts the architecture creates a second, undocumented source of truth, which is worse than a gap.

For any non-trivial change, before implementing:

1. Analyse the affected architecture
2. Identify dependencies
3. Identify reusable components — check `platform-commons` and `design-system` before writing anything new
4. State the implementation plan

---

## Branch strategy

```
main       protected. Always releasable. Tagged releases only.
develop    integration branch. CI must be green.
feature/<module>-<short-description>
fix/<module>-<short-description>
docs/<short-description>
```

Branch from `develop`; merge back via pull request. `main` receives `develop` only at a release.

---

## Commits

Conventional Commits, scoped to the module:

```
feat(track-correlation): add Kalman state estimation
fix(auth-service): reject refresh token reuse
docs(architecture): add section 11 repository strategy
chore(contracts): regenerate TypeScript bindings
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`, `build`, `ci`.

**Every commit leaves the codebase working** — it compiles, tests pass, the application starts. If a change is too large for that, it is too large for one commit.

---

## Pull requests

Requirements:

- [ ] CI green — lint, contract check, tests, coverage gate, security scan
- [ ] New code follows [docs/CODING-STANDARDS.md](docs/CODING-STANDARDS.md)
- [ ] Architectural layering respected — `domain/` imports no framework
- [ ] Tests cover the behaviour, not just the lines
- [ ] Every new endpoint has validation, exception handling, logging, DTOs, OpenAPI, and authorisation
- [ ] No secrets, credentials, or `.env` files
- [ ] `ARCHITECTURE.md` updated if the change affects design

One approving review before merge. Squash-merge into `develop`.

---

## Contract changes

Changes under `contracts/proto/` affect all three tracks simultaneously and warrant particular care.

- `buf lint` and `buf breaking --against main` run in CI; an incompatible change fails the build
- Backward compatibility is the default: add optional fields; never reuse or repurpose a field number
- A genuinely breaking change requires a topic major version bump and a documented dual-write migration (§5.5)
- Regenerate bindings for **all three** languages and commit the change atomically

---

## Working across tracks

Track A (platform) and Track B (AI) develop in parallel and meet only at the `InferenceService` contract.

- Track A never imports Track B code. Track B never imports Track A code.
- Track B integrates against the shared conformance suite (`ai-services/*/tests/test_contract.py`). A service that passes it is integrable by definition — no negotiation required.
- Integrating a real model is a configuration change (`AI_PROVIDER_MODE`, endpoint, model version) plus a container image tag. If it regresses, revert the config; do not patch across the boundary.

---

## Scope constraint

This platform produces **advisory recommendations only**. Engagement actions — RF jamming, GNSS spoofing, cyber takeover — are outside the software boundary, and every recommendation terminates at an operator authorisation gate.

Do not add a code path that emits an RF-active or link-altering command. The recommendation vocabulary is closed (§6.3.4); extending it requires an architecture amendment and supervisor approval, not a pull request.
