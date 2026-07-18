# Contracts

Protobuf definitions shared by the platform services, the AI services, and the
frontend. Single source of truth for every wire format in the system.

## Contents to be authored

| File | Specification | Why it matters |
|---|---|---|
| `proto/detection.proto` | ARCHITECTURE.md section 5.3 | The canonical `DetectionRecord`. Every modality normalises into this shape; it is what makes the platform sensor-agnostic. |
| `proto/inference.proto` | ARCHITECTURE.md section 6.2 | The `InferenceService` contract. **The single boundary between Track A and Track B.** |
| `proto/track.proto` | Section 4.1.5 | Track lifecycle events. |
| `proto/alert.proto` | Section 4.1.8 | Alert and incident events. |

Both specifications are already written in full in ARCHITECTURE.md and can be
lifted verbatim.

## Priority

Per ARCHITECTURE.md sections 1.4 and 6.1, the inference contract is frozen at
the end of Week 2 and versioned thereafter. Until `detection.proto` and
`inference.proto` exist, Track B has nothing to build against and the
mock-provider decoupling does not function. **These are the highest-value files
in the repository.**

## Rules

- `generated/` is build output. Never edit by hand, never commit manually.
- `buf breaking` runs in CI against `main`; an incompatible change fails the
  build unless the topic major version is bumped (section 5.4).
- Backward compatibility is enforced: add optional fields, never reuse or
  repurpose a field number.

## Status

Scaffold only. No `.proto` files authored yet.
