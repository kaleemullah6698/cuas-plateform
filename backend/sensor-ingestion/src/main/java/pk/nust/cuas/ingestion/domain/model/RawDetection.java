package pk.nust.cuas.ingestion.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * The fields of an incoming detection that the ingestion domain reasons about, projected out of the
 * canonical {@code DetectionRecord} (ARCHITECTURE.md section 5.3).
 *
 * <p>The full record — position, kinematics, and the modality-specific measurements — is carried
 * through by the infrastructure layer untouched; the domain only needs the header to validate,
 * deduplicate, correct the clock, and assign identity. Keeping the domain to this projection is
 * what lets it stay free of the generated protobuf types.
 *
 * @param sensorId      the reporting sensor; the partition and dedup key
 * @param modality      the sensor modality
 * @param sensorTime    the timestamp the sensor reported, as reported, or empty when absent
 * @param sensorSeq     the per-sensor monotonic sequence number, the dedup discriminator
 * @param quality       the sensor-reported confidence in [0,1]
 * @param correlationId the end-to-end trace id carried on the record, possibly blank
 */
public record RawDetection(
        String sensorId,
        Modality modality,
        Optional<Instant> sensorTime,
        long sensorSeq,
        float quality,
        String correlationId) {

    public RawDetection {
        if (sensorTime == null) {
            throw new IllegalArgumentException("sensorTime must not be null; use Optional.empty()");
        }
        correlationId = correlationId == null ? "" : correlationId;
    }
}
