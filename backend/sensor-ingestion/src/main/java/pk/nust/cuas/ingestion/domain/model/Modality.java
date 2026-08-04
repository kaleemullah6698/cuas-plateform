package pk.nust.cuas.ingestion.domain.model;

/**
 * Sensor modality, the domain mirror of the {@code SensorModality} enum in the detection contract
 * (ARCHITECTURE.md section 5.3).
 *
 * <p>Held as a domain type rather than the generated protobuf enum so the domain compiles without
 * protobuf on the classpath. The two are mapped explicitly at the infrastructure boundary.
 */
public enum Modality {
    UNSPECIFIED,
    RF,
    RADAR,
    EO_IR,
    ACOUSTIC,
    GNSS,
    EXTERNAL
}
