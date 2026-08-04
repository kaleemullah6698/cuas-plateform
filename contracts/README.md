# Contracts

Protobuf definitions shared by the platform services, the AI services, and the
frontend. Single source of truth for every wire format in the system.

## Contents

| File | Specification | Status |
|---|---|---|
| `proto/detection.proto` | ARCHITECTURE.md section 5.3 | **Authored (M1).** The canonical `DetectionRecord`; every modality normalises into it. |
| `proto/inference.proto` | ARCHITECTURE.md section 6.2 | **Authored (M1).** The `InferenceService` contract — the single boundary between Track A and Track B. |
| `proto/ingestion.proto` | ARCHITECTURE.md sections 4.1.4, 5.3.1 | **Authored (M4.0).** The `DetectionIngestService` gRPC intake, `IngestAck`, and `SensorHealth`. `DetectionRecord` is the streamed payload. |
| `proto/track.proto` | Section 4.1.5 | Deferred to M5 — event field schemas are defined when track-correlation defines them. |
| `proto/alert.proto` | Section 4.1.8 | Deferred to M7 — same reason, with alert-service. |

## Generating bindings

```bash
buf lint                                 # STANDARD ruleset, exceptions documented in buf.yaml
buf breaking --against '.git#branch=main'
buf generate                             # -> generated/{java,python,typescript}
```

The Java bindings are also produced and compiled by the Maven module
`backend/contracts` (`mvn -pl contracts -am verify`), which runs `buf generate`
in `generate-sources` and compiles `generated/java` into the shared contracts jar.
`buf` must be on `PATH` (a documented prerequisite).

## Rules

- `generated/` is build output. Never edit by hand, never commit manually (git-ignored).
- `buf breaking` runs in CI against `main`; an incompatible change fails the
  build unless the topic major version is bumped (section 5.4).
- Backward compatibility is enforced: add optional fields, never reuse or
  repurpose a field number.
- Lint exceptions in `buf.yaml` exist only where a STANDARD rule conflicts with the
  contract fixed by ARCHITECTURE.md; each is documented in place.

## Status

M1 complete. `detection.proto` and `inference.proto` authored and verified to
generate, compile, import, and round-trip in Java, Python, and TypeScript.

M4.0 adds `ingestion.proto` (`DetectionIngest`, `IngestAck`, `SensorHealth`), verified with
`buf lint`, `buf breaking` (additive — no topic version bump), and `buf generate`.
