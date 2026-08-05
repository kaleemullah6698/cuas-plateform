# ai-orchestrator

The Track A / Track B boundary: inference routing, circuit breaking, mock fallback.

- **Port:** 8085 (HTTP)
- **Database:** none. Redis for the inference cache (§4.1.6)
- **Specification:** `docs/ARCHITECTURE.md` §4.1.6, §6.1, §6.2, §6.4

## The one rule this service exists to keep

**The orchestrator must never know a model by name.** §6.1 promises that adding a fifth model —
acoustic, GNSS, radar fusion, anything — is *a configuration entry, not a code change anywhere in
Track A*. Everything here follows from that:

- Services are a **map keyed by id** in `application.yml`, never a fixed set of fields.
- Routing is by **sensor modality**, which describes the *input*: "this came from an RF sensor" is a
  fact about the sensor; "send this to the RF classifier" would be a fact about a model.
- `ServiceId` is an opaque string. `AiServiceDescriptor` has no per-model field and no subclass.
- Every call goes through the `InferenceProvider` port, which takes the descriptor as an argument
  precisely so **one provider implementation serves many services** — otherwise the orchestrator
  would need a bean per model, which is the code change §6.1 forbids.

`ModelAgnosticismTest` enforces this mechanically: it scans this module's source for the service and
framework names in §6.3 and fails if any appears — comments included. The check is deliberately
blunt rather than comment-aware, because naming a model in prose is the first step towards branching
on it. It caught a violation on its first run (a javadoc example), which is the point.

## Registering a service

```yaml
ai:
  services:
    any-id-you-like:
      modalities: [RF, RADAR]   # routed here when a request carries either; invoked once
      mode: mock                # or grpc
      endpoint: some-host:9001  # required when mode is grpc
      timeout: PT0.1S           # orchestrator-enforced budget
      model-version: "1.3.0"    # optional pin; blank = service default
      max-concurrent: 32        # bulkhead permits
```

Configuration is validated **at startup** — an unknown modality or mode, a duplicate id, or a `grpc`
service with no endpoint fails the context. A misconfigured AI service that only reveals itself under
load is precisely the failure this system cannot afford; a boot error is cheap by comparison.

A service accepting several modalities is routed for any of them but **invoked once per request** —
otherwise a fusion model would be called twice for one track, doubling GPU load and producing two
competing results for the same object.

## Integrating a trained model

The path from a trained model to production touches **no Java**:

1. Export the weights (ONNX or equivalent) and register them — MLflow, content-addressed in MinIO by
   SHA-256 (§6.6).
2. Wrap them in the §6.7 container template, implementing the `InferenceService` gRPC contract (§6.2)
   and passing the shared `tests/test_contract.py` conformance suite.
3. Change **one line**: `mode: mock` → `mode: grpc`, plus an `endpoint`.

Reverting is the same edit backwards, which is what makes a regressing model a rollback rather than
an incident. No orchestrator code changes at any step.

## `mock` is not a placeholder

The mock provider is a **permanent, production-quality component** (§6.4). It backs demonstrations,
CI and regression testing after real models ship, and it is the fallback an open circuit breaker
falls onto (§6.5). Its results are badged `model_version: 0.0.0-mock` and `device: DEVICE_MOCK` so an
operator, a reviewer, or a demo audience can always tell a mocked number from a real one — a safety
property for a defence-adjacent system, not cosmetics.

## Layering

`domain/` is framework-free **and contract-free**: `Modality` is the domain's own enum, mapped from
`detection.v1.SensorModality` by `common/ModalityMapper` at the boundary, so the domain compiles
without Spring or the generated types (CLAUDE.md §2). An unspecified or unrecognised modality maps to
*absent*, never to a default — routing an unidentifiable observation "somewhere" would produce a
confident-looking result from a model that never saw that kind of input.

`InferenceProvider` sits in `application/port/` rather than `domain/port/` and speaks the **generated
Protobuf types**. That is deliberate: §6.2 *is* the contract, and §6.4 requires the mock to produce
schema-identical output so conformance is compile-enforced. A private translation layer would let the
mock and a real service drift apart in exactly the way the shared conformance suite exists to prevent.

Providers signal an *inference* failure through the response `status`
(`STATUS_MODEL_ERROR`, `STATUS_LOW_CONFIDENCE`, `STATUS_INSUFFICIENT_DATA`) and throw only on a
*transport* failure. The circuit breaker measures the latter; conflating them would open a breaker
because a model honestly reported low confidence.

## Status

**M6.1 complete** — the registry, modality routing, the `InferenceProvider` port, and
startup-validated configuration. No provider implementations yet, so a request routes to a set of
descriptors and stops there; the service starts and logs its registry.

Still to come: M6.2 the mock provider; M6.3 resilience (timeout, breaker, bulkhead,
`ai.service.degraded.v1`); M6.4 Kafka and the content-hash cache; M6.5 the gRPC provider, canary
split and admin API; M6.6 the two M5 items deferred here — track-correlation's circuit-broken `Infer`
call and the `ai.inference.completed.v1` consumer behind `track.classified.v1`.
