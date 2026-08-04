# sensor-simulator

A development sensor adapter: it generates synthetic detections and drives them into
sensor-ingestion through the **real** REST and gRPC intake endpoints.

- **Specification:** `docs/ARCHITECTURE.md` section 4.1.4
- **Runs headless** — a client, not a service. It boots, optionally runs one simulation, and exits.

## Why it goes through the real endpoints

The whole value of the simulator is that it takes no shortcuts: it never writes to Kafka or
PostgreSQL. It POSTs canonical `DetectionRecord` JSON to `POST /ingest/detections` and streams
records over `DetectionIngestService.StreamDetections`, exactly as a real sensor adapter would. So a
run exercises validation, the raw topic, normalisation, de-duplication, and health telemetry — the
entire pipeline — with reproducible input.

## Running a simulation

```bash
# REST intake, 3 sensors × 100 detections (the defaults):
SIMULATOR_RUN_ON_STARTUP=true mvn -pl backend/sensor-simulator -am spring-boot:run

# gRPC intake:
SIMULATOR_RUN_ON_STARTUP=true SIMULATOR_TRANSPORT=GRPC \
  mvn -pl backend/sensor-simulator -am spring-boot:run

# Both intakes, larger load, against a remote ingestion host:
SIMULATOR_RUN_ON_STARTUP=true SIMULATOR_TRANSPORT=BOTH \
  SIMULATOR_SENSORS=10 SIMULATOR_DETECTIONS_PER_SENSOR=1000 \
  INGEST_REST_URL=http://ingestion:8083 INGEST_GRPC_HOST=ingestion \
  mvn -pl backend/sensor-simulator -am spring-boot:run
```

`run-on-startup` is off by default, so booting the module without it does nothing — importing or
testing the module never generates traffic.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `simulator.rest-base-url` | `http://localhost:8083` | Ingestion REST base (its own port, not the gateway) |
| `simulator.grpc-host` / `.grpc-port` | `localhost` / `9083` | Ingestion gRPC intake |
| `simulator.transport` | `REST` | `REST`, `GRPC`, or `BOTH` |
| `simulator.sensors` | `3` | Distinct simulated sensors; modalities rotate RF → radar → EO/IR |
| `simulator.detections-per-sensor` | `100` | Detections each sensor emits |
| `simulator.batch-size` | `50` | Detections per REST request |
| `simulator.run-on-startup` | `false` | Fire a run on boot |

## Not in this module

The simulator emits detections; it does not simulate a sensor going silent to force a `DEGRADED` or
`OFFLINE` transition — that lifecycle is a property of the ingestion service's health emitter and is
tested there. Randomised fault injection and sustained-rate load profiles are possible extensions.
