# vision-detector

- **Port:** 9002
- **Specification:** `docs/ARCHITECTURE.md` section 6.3
- **Status:** Active in the 16-week scope.

## Contract

Implements the `InferenceService` gRPC contract (ARCHITECTURE.md section 6.2).
This service is a pure function of its input: it has no database credentials,
publishes to no Kafka topic, and has no network egress (section 7.1).

`tests/test_contract.py` is the shared conformance suite — the identical file
runs against every AI service and against the mock provider. A service that
passes it is integrable by definition.

## Status

Scaffold only. No model code, training code, or gRPC implementation.
