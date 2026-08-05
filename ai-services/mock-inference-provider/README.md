# mock-inference-provider

Reference implementation of the `InferenceService` contract that returns
schema-valid synthetic results with realistic latency distributions
(ARCHITECTURE.md section 6.4).

This is the mechanism that decouples Track A from Track B, and it is a
first-class deliverable — not a stub. It is what allows the entire platform
to run end-to-end with zero trained models.

## Where it lives

**In-process, inside `backend/ai-orchestrator`** —
`infrastructure/provider/MockInferenceProvider`, with its deterministic engine in `domain/service/`
(`ScenarioLibrary`, `LatencyModel`, `FailureInjector`).

§6.4 specifies it as a Java component behind the `InferenceProvider` port, not as a separate
container, and that matters: the mock is the fallback an **open circuit breaker** falls onto (§6.5),
so it must be reachable when the network is exactly what has failed. A mock served over the same
gRPC transport as the real services could be unavailable for the same reason they are, leaving the
orchestrator with nothing to answer with.

This directory therefore documents the component; it holds no separate service.

## Non-negotiable behaviour

- Reports `model_version: "0.0.0-mock"` and `device: DEVICE_MOCK`, so mocked
  results are **never** mistakable for real ones. The dashboard renders an
  explicit MOCK INFERENCE badge from these fields. A pinned canary version is *appended*
  (`0.0.0-mock+pinned-1.3.0`), never substituted — the mock cannot claim to be a real version.
- Deterministic: seeded by `trackId`, so the same track always yields the same
  prediction. Tests and demos are reproducible.
- Realistic latency: samples a lognormal distribution matched to each service's
  documented p95 budget.
- Failure injection: configurable timeout, model-error, and low-confidence
  rates, so every UI degraded state is exercised before a real model exists.
- **Serves every registered service and knows none of them.** The descriptor arrives as a parameter,
  so one instance stands in for any model, present or future.

## The scenario library

Six scenarios, each driving a distinct operator-facing state. A track is assigned one
deterministically from its id.

| Scenario | What it exercises |
|---|---|
| `SINGLE_INTRUSION` | The ordinary confident drone classification |
| `SWARM` | Multi-target rendering and alert grouping |
| `FALSE_POSITIVE_BIRD` | A **confident** non-drone the operator must be able to dismiss |
| `SENSOR_DROPOUT` | `INSUFFICIENT_DATA` — the "factor missing" path of §6.3.4 |
| `LOW_CONFIDENCE_AMBIGUOUS` | Low confidence, high entropy, self-reported unreliable |
| `CONTRADICTORY_MODALITIES` | Near-equal competing classes — the disagreement case of §2.5.6 |

`CONTRADICTORY_MODALITIES` reports `is_reliable: true` deliberately: the model is not
malfunctioning, the world is genuinely ambiguous, and those are different states.

The labels are drone and non-drone *classes* — facts about what is in the air. They are not service
or model names, and the library does not vary by service.

## Configuration

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

Defaults describe a healthy system. Injection is opt-in because it exists to *force* a state on
demand — for a demo of the degraded UI, a resilience test, or a soak run — not to make ordinary
development flaky. The three rates are drawn from **disjoint bands of one value**, so they compose:
10% + 10% + 10% is 30% failure overall, not the 27% independent draws would give.

`simulate-latency: false` still reports a realistic figure but does not sleep — used by unit tests
that assert on content. It stays **on** elsewhere, because a mock that returns instantly makes every
timeout, circuit breaker and latency-budget assertion meaningless.

## Why deterministic rather than random

Every decision derives from a 64-bit FNV-1a seed over the track and service ids. `String.hashCode`
would not do: track ids are UUIDv7 and share a long common prefix by construction, so a weak 32-bit
hash clusters consecutive tracks into one scenario — defeating the point of a library.

Determinism is what separates a demonstrable defect from one that "happens sometimes". A
`java.util.Random` here would make every injected failure unreproducible.

## Latency distribution

Lognormal, fitted so p95 lands on the service's configured budget: with `sigma` fixed,
`mu = ln(p95) − 1.645·sigma`. Inference latency really is clustered near a median with a long right
tail from queueing and batching; a constant or uniform would make the system look better-behaved
under mock than it ever will under a real model. The fit also means a service's timeout is hit
*occasionally but not usually* — exactly the condition a circuit breaker needs to be exercised
against.

## Status

**Implemented (M6.2).** Permanent: it remains after real models ship, backing CI, integration tests,
demonstrations, regression testing and offline development, and serving as the breaker's fallback.
