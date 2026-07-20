---
name: integrate-ai-service
description: Wire an AI inference service, mock provider, or the inference contract into the platform — gRPC InferenceService, AI Orchestrator provider selection, circuit breaking, mock fallback, model versioning. Use when working on ai-orchestrator, an ai-services container, the mock provider, or contracts/proto/inference.proto.
---

# Integrating an AI service

`docs/ARCHITECTURE.md` §6 governs. This is the Track A / Track B boundary — the single most important decoupling in the system.

## The principle

The AI Orchestrator is the **only** platform component that knows an AI service exists. Adding a model is a configuration entry, not a code change anywhere else in Track A.

Every AI service:
- Implements one gRPC contract: `InferenceService` (§6.2)
- Exposes `/health/live`, `/health/ready`, `/metrics`, `/model/info`
- Declares `model_version` and `model_sha` on **every** response
- Is a pure function of its input — **no database credentials, no Kafka access, no network egress** (§7.1)

An AI service that writes to a database or publishes an event is a contract violation. Full stop.

## The contract is the critical path

`contracts/proto/inference.proto` and `contracts/proto/detection.proto` are currently empty. Both are written in full in §6.2 and §5.3 and lift verbatim. **Author these before anything else** — until they exist, nothing in Track B can be built and the mock provider cannot compile.

Rules once authored:
- `model_version`/`model_sha` always populated — including by the mock (`0.0.0-mock`)
- Confidence calibrated, in `[0,1]` — raw softmax is a violation
- A service never returns empty; absence of detection is `label:"background"` with a confidence, not silence
- Timeout enforced by the orchestrator; an over-budget service is cancelled and its result discarded

## Mock providers — how Track A ships before Track B

The mock is a first-class deliverable (§6.4), not a stub. It is what lets the whole platform run end-to-end with zero trained models.

```java
public interface InferenceProvider {
    InferenceResponse infer(InferenceRequest request);
    ModelInfo modelInfo();
    boolean isHealthy();
}
```

Mock requirements:
- Schema-identical output — same generated Protobuf classes as the real service
- Realistic latency — sample a lognormal distribution matched to each service's §6.3 p95 budget
- Deterministic — seeded by `trackId`, so a track always yields the same prediction; tests and demos reproduce
- Failure injection — configurable timeout / error / low-confidence rates, exercising every UI degraded state before a real model exists
- **Never silently promoted** — `model_version:"0.0.0-mock"`, `device:DEVICE_MOCK`; the dashboard renders an explicit MOCK INFERENCE badge from these fields

Provider selection is per-service and runtime-configurable (`AI_PROVIDER_MODE`, endpoint, model version). Switching mock→real is a config change plus an image tag. If a real model regresses, revert the config — never patch across the boundary.

## Orchestrator responsibilities (§6.1, §6.5)

- Route by modality to the correct service
- Enforce per-service timeout and a Resilience4j circuit breaker (50% failure over 20 calls → open 30s)
- Bounded concurrency per downstream service (bulkhead, ~32) so a burst never overwhelms a GPU
- On breaker-open or deadline: fall back to the mock or last-known result, publish `ai.service.degraded.v1`, and **never drop the track** — degrade it visibly
- Cache identical inferences (content-hash key, 60s TTL)
- Canary split by `hash(track_id)` so a track is never scored by two versions in its lifetime

## The resilience requirement

The backend must keep working when an AI service is down. This is not optional — it is the reason for the orchestrator's existence. A track with no inference is scored on kinematics alone and flagged `inference_unavailable`. The operator always sees what produced the number.

## Conformance

`ai-services/*/tests/test_contract.py` is the shared suite — identical for every AI service and the mock. A service that passes it is integrable by definition, with no negotiation between tracks. Run it before declaring any service ready.

## Checklist

- [ ] `detection.proto` and `inference.proto` authored from §5.3 / §6.2
- [ ] Service implements `InferenceService` and the four health/info endpoints
- [ ] Service has no DB credentials, no Kafka, no egress
- [ ] `model_version` and `model_sha` on every response
- [ ] Confidence calibrated, in `[0,1]`
- [ ] Mock provider is deterministic, latency-realistic, failure-injecting, badge-flagged
- [ ] Orchestrator has timeout, circuit breaker, bulkhead, and a documented fallback
- [ ] Conformance suite passes
