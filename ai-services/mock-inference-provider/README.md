# mock-inference-provider

Reference implementation of the `InferenceService` contract that returns
schema-valid synthetic results with realistic latency distributions
(ARCHITECTURE.md section 6.4).

This is the mechanism that decouples Track A from Track B, and it is a
first-class deliverable — not a stub. It is what allows the entire platform
to run end-to-end with zero trained models.

## Non-negotiable behaviour

- Reports `model_version: "0.0.0-mock"` and `device: DEVICE_MOCK`, so mocked
  results are **never** mistakable for real ones. The dashboard renders an
  explicit MOCK INFERENCE badge from these fields.
- Deterministic: seeded by `trackId`, so the same track always yields the same
  prediction. Tests and demos are reproducible.
- Realistic latency: samples a lognormal distribution matched to each service's
  documented p95 budget.
- Failure injection: configurable timeout, model-error, and low-confidence
  rates, so every UI degraded state is exercised before a real model exists.

## Status

Scaffold only.
