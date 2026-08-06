# Contracts

Protobuf definitions shared by the platform services, the AI services, and the
frontend. Single source of truth for every wire format in the system.

## Contents

| File | Specification | Status |
|---|---|---|
| `proto/detection.proto` | ARCHITECTURE.md section 5.3 | **Authored (M1).** The canonical `DetectionRecord`; every modality normalises into it. |
| `proto/inference.proto` | ARCHITECTURE.md section 6.2 | **Authored (M1).** The `InferenceService` contract — the single boundary between Track A and Track B. |
| `proto/ingestion.proto` | ARCHITECTURE.md sections 4.1.4, 5.3.1 | **Authored (M4.0).** The `DetectionIngestService` gRPC intake, `IngestAck`, and `SensorHealth`. `DetectionRecord` is the streamed payload. |
| `proto/track.proto` | ARCHITECTURE.md sections 4.1.5, 5.3.2 | **Authored (M5.1).** The `Track` message and `TrackStatus` lifecycle carried on `track.created.v1` and `track.updated.v1`. Reuses `detection.v1.GeoPosition`. |
| `proto/threat.proto` | ARCHITECTURE.md sections 4.1.7, 5.3.3, 6.3.4 | **Authored (M7.A).** `ThreatAssessment`, the closed `ThreatLevel`, `ThreatFactorType` and `Recommendation` vocabularies, and the two transition events. |
| `proto/alert.proto` | ARCHITECTURE.md sections 4.1.8, 5.3.4, 2.4.3 | **Authored (M7.A).** `Alert`, `Incident`, `IncidentEvent`, `EvidenceRef`, `DecisionChain`. Reuses `threat.v1.ThreatLevel` as severity. |
| `proto/notification.proto` | ARCHITECTURE.md sections 4.1.9, 5.3.5 | **Authored (M7.A).** `NotificationSent` and `NotificationFailed` — delivery outcomes, never addresses or message bodies. |

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

M5.1/M5.3/M5.7 add `track.proto` and extend it additively (`SubscribeTracks`, `TrackClassification`,
`KinematicEstimate`, CPA, `TRACK_STATUS_MERGED`).

M7.A adds `threat.proto`, `alert.proto` and `notification.proto` — three new files, so nothing here
can break an existing consumer. Verified with `buf lint`, `buf breaking` against `main`, and
`mvn -pl contracts -am verify` (40 tests).

**Two closed vocabularies are enforced by test, not only by prose.** `ThreatSerdeTest` asserts that
`Recommendation` holds exactly §6.3.4's five advisory values, and `AlertSerdeTest` asserts that
`EngagementAction` holds exactly one legal value, `NONE`. Both fail the build the moment a value is
added. Extending either is an architecture amendment with supervisor approval — this is what makes
the platform's advisory-only scope limit a property of the build rather than of the document alone.
