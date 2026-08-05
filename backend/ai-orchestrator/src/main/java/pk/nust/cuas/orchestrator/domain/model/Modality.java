package pk.nust.cuas.orchestrator.domain.model;

/**
 * A sensing modality — what kind of observation a detection is (ARCHITECTURE.md section 5.3,
 * {@code SensorModality}).
 *
 * <p>The orchestrator's routing key. It describes the <em>input</em>, never the model that consumes
 * it: "this detection came from an RF sensor" is a fact about the sensor, whereas "send this to the
 * RF classifier" would be a fact about a model. That distinction is what keeps section 6.1's promise
 * that adding a model is a configuration entry rather than a code change — a new service declares
 * which modalities it accepts, and routing follows automatically.
 *
 * <p>The domain's own enum rather than the generated {@code detection.v1.SensorModality}, mapped at
 * the boundary, so the domain compiles without the contract on the classpath (CLAUDE.md section 2).
 */
public enum Modality {
    RF,
    RADAR,
    EO_IR,
    ACOUSTIC,
    GNSS,
    EXTERNAL
}
