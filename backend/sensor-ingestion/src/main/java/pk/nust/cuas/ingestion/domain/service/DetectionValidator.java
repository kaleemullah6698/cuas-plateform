package pk.nust.cuas.ingestion.domain.service;

import java.util.ArrayList;
import java.util.List;
import pk.nust.cuas.ingestion.domain.model.Modality;
import pk.nust.cuas.ingestion.domain.model.RawDetection;

/**
 * Validates the header of an incoming detection at the boundary (ARCHITECTURE.md section 4.1.4:
 * "schema validation").
 *
 * <p>Checks the invariants the pipeline depends on downstream: a detection with no sensor cannot be
 * partitioned or attributed, one with no modality cannot be routed to a classifier, one with no
 * sensor time cannot have its clock corrected, and a quality outside [0,1] is not the calibrated
 * confidence the fusion stage assumes. The modality-specific measurement payload is deliberately
 * not inspected here — the canonical schema already types it, and per-modality plausibility belongs
 * to the classifiers, not the ingest boundary.
 *
 * <p>Pure: it returns the list of violations rather than throwing, so both the REST and gRPC
 * boundaries can decide how to surface them.
 */
public final class DetectionValidator {

    /**
     * @param detection the projected header to validate
     * @return the violations found, empty when the detection is valid
     */
    public List<String> validate(RawDetection detection) {
        List<String> violations = new ArrayList<>();
        if (detection.sensorId() == null || detection.sensorId().isBlank()) {
            violations.add("sensorId is required");
        }
        if (detection.modality() == null || detection.modality() == Modality.UNSPECIFIED) {
            violations.add("modality is required and must not be UNSPECIFIED");
        }
        if (detection.sensorTime().isEmpty()) {
            violations.add("sensorTime is required");
        }
        float quality = detection.quality();
        if (Float.isNaN(quality) || quality < 0.0f || quality > 1.0f) {
            violations.add("quality must be within [0,1]");
        }
        return List.copyOf(violations);
    }
}
