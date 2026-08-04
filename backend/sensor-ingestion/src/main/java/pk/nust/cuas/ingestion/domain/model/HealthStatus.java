package pk.nust.cuas.ingestion.domain.model;

/**
 * Liveness of a sensor as judged from how recently it last reported (ARCHITECTURE.md section
 * 4.1.4). Mirrors the {@code SensorHealth.Status} enum in the ingestion contract; mapped to the
 * generated protobuf enum at the messaging boundary.
 */
public enum HealthStatus {
    ONLINE,
    DEGRADED,
    OFFLINE
}
