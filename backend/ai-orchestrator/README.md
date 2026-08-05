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

The default profile registers **nothing** — `services: {}`. A default that shipped example services
would register them in every deployment including production, which is the configuration-level
version of mistaking a mock for a real model. `application-dev.yml` carries a worked example of all
four §6.3 services; a deployment states its own.

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

## Mock configuration

```yaml
ai:
  mock:
    simulate-latency: true    # actually sleep for the sampled time
    latency-sigma: 0.35       # lognormal shape; median lands near 60% of p95
    failure:
      timeout-rate: 0.0       # the transport failure a breaker counts
      model-error-rate: 0.0   # a model's answer, not a transport failure
      low-confidence-rate: 0.0
```

See `ai-services/mock-inference-provider/README.md` for the scenario library and the reasoning
behind determinism and the latency distribution.

## Resilience

Every call goes through `ResilientInferenceDispatcher`, which layers three protections **per
service** and never lets a provider failure reach the caller. §5.6's governing principle applies: a
drone scored late is a degraded outcome, a drone never scored is an undetected one — so **the track
is never dropped, only degraded**.

| Layer | Question it answers | On rejection |
|---|---|---|
| Bulkhead (`max-concurrent`, wait 0) | Is this service already at its concurrency limit? | `BULKHEAD_FULL` |
| Circuit breaker (50% / 20 calls → open 30 s) | Has this service been failing? | `CIRCUIT_OPEN` |
| Time limiter (`timeout`) | Did *this* call answer in budget? | `TIMEOUT` |

Anything else transport-level becomes `TRANSPORT_ERROR`. All four fall back to the mock and notify
the `DegradationListener` port (which M6.4 wires to `ai.service.degraded.v1`).

**The breaker sits inside the bulkhead.** A bulkhead rejection means *busy*, not *broken*; counting
it would let a traffic spike take a healthy service offline.

**Only transport failures reach the breaker.** A model reporting `MODEL_ERROR`, `LOW_CONFIDENCE` or
`INSUFFICIENT_DATA` has answered — that is its verdict, not evidence of unreachability — so those
pass through and count as breaker *successes*. Otherwise the breaker would fire hardest exactly when
the airspace is most ambiguous.

**But a response that *reports* a transport failure is lifted into a thrown one.** The mock returns
`STATUS_TIMEOUT` under injected failure rather than throwing, and a gRPC client may surface a
server-side deadline the same way; without lifting, an injected timeout would count as a success and
the breaker could never open.

Breaker and bulkhead state are exported to Micrometer, so `/actuator/prometheus` shows open breakers
and available permits per service.

## Events and caching

One request fans out to every service its modalities route to. For each: check the cache, dispatch
under the resilience layer, publish the outcome.

| Topic | Key | When |
|---|---|---|
| `ai.inference.requested.v1` | `trackId` | A call was dispatched — **not** on a cache hit |
| `ai.inference.completed.v1` | `trackId` | A usable result (OK, low-confidence, degraded) |
| `ai.inference.failed.v1` | `trackId` | No usable result (model error, insufficient data, timeout) |
| `ai.service.degraded.v1` | `serviceId` | A fallback answered; emitted **alongside** completed |

A cache hit deliberately skips `requested`: that topic measures how much inference the system
actually asks for, and counting an answer that never reached a provider would hide the cache's value
in the one metric that should show it.

**The cache is content-addressed** (§4.1.6, TTL 60 s) — a SHA-256 over the detection fingerprints
plus the options that change what the question means. Keying by `trackId` would be the obvious
shortcut and wrong: a track is a thing that *changes*, so its second inference is usually a different
question, and answering it from the first would freeze a moving drone's classification. A pinned
model version is part of the key, or a canary would be served the incumbent's cached answer.

**Degraded results are never cached** — a fallback describes the service's health at one moment, not
the input; caching it would keep serving a mock answer for a full TTL after recovery.

**Publication never fails an inference.** Sends are logged-and-dropped on failure, the opposite of
track-correlation's synchronous publish. There, a lost event means a track nothing downstream knows
about; here the caller already holds its answer, and failing it because telemetry could not be
written would convert a successful inference into a failed one.

## Admin API

| Endpoint | Permission | Purpose |
|---|---|---|
| `GET /ai/services` | `ai:configure` | The registry plus each service's live health |
| `POST /ai/services/{id}/model-version` | `ai:configure` | §6.6's rollback and canary lever |

Both require `ai:configure`, which §7.4 grants to ENGINEER and ADMIN only — changing which model
scores a threat changes what the system concludes about the airspace. The **read** is guarded too:
the registry discloses every service's endpoint and health, a map of internal topology. Health is
*reported*, never thrown: an admin asking which services are up must get an answer about all of them,
not a 500 because one is down.

Only the version fields are mutable at runtime. An id, its modalities, its mode and its endpoint are
the deployment's shape; letting those change over HTTP would allow rerouting the pipeline through an
API call.

## Canary

`POST .../model-version` accepts a `canaryVersion` and a `canaryPercentage`. The split is **by
track**, hashed from `trackId` and the service id:

- **By track, not per call** — a track keeps its assignment for life. Splitting per request would
  mean one drone's successive classifications came from two models, so a change in its threat level
  could be the model changing rather than the situation, and an operator could not tell which.
- **The service id is in the seed**, so a track is not systematically on every service's canary at
  once; correlated assignment would make one model's regression look like another's.
- **Deterministic**, so the same track resolves the same way on every orchestrator instance and
  across restarts. A `Random` here would make the canary's results meaningless.

## What track-correlation calls

The orchestrator **implements the same `InferenceService` contract it consumes** (port 9085). §6.5's
sequence is then literal: track-correlation issues `Infer`, the orchestrator fans out by modality,
and one answer comes back. Defining a second, orchestrator-specific RPC would be another contract to
maintain and would let the two drift.

A request may route to several services, but the caller asked one question about one track, so the
**most confident usable** result is returned. Confidence is calibrated and comparable across services
by construction (§6.2's `ConfidenceReport`), so this is a meaningful ordering rather than an
arbitrary pick — and every individual result still reaches Kafka, so threat-assessment can weigh
cross-sensor agreement itself (§6.3.3). When nothing routes or every service failed, the response is
`INSUFFICIENT_DATA` rather than an error: no model was in a position to judge, which is a fact about
the input rather than a fault.

Authorisation is transport-level (§7.2, mTLS workload identity), as on the other inter-service gRPC
surfaces. The REST endpoints are the user-facing ones and carry `@PreAuthorize`.

## Status

**M6 complete — M6.1 through M6.6** — the registry, modality routing, the `InferenceProvider` port,
startup-validated configuration, the mock provider, and the resilience layer. A request can be
routed, dispatched under timeout/breaker/bulkhead, cached, answered or degraded, and published on all
four topics.

The boundary is finished: a detection routes by modality, is dispatched under timeout/breaker/bulkhead
with a content-addressed cache, answers or degrades to the mock, publishes on all four topics, and
reaches track-correlation over gRPC — with zero trained models present.

**Integrating a real model remains a configuration change.** `mode: mock` → `mode: grpc` plus an
`endpoint`, and nothing in Track A moves.
