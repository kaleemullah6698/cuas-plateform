package pk.nust.cuas.ingestion.application;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Optional;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.ingestion.domain.model.Modality;
import pk.nust.cuas.ingestion.domain.model.RawDetection;

/**
 * Translates between the canonical {@code DetectionRecord} contract (ARCHITECTURE.md section 5.3)
 * and the ingestion domain's {@link RawDetection} projection.
 *
 * <p>This is the anti-corruption boundary: it is the one place the generated protobuf types meet
 * the domain, which is what keeps the domain free of them. Only the header is projected — the
 * measurement payload rides through untouched on the record itself and never enters the domain.
 */
final class DetectionMapper {

    private DetectionMapper() {
    }

    /** Projects the header the domain reasons about out of a canonical record. */
    static RawDetection toRawDetection(DetectionRecord record) {
        return new RawDetection(
                record.getSensorId(),
                toModality(record.getModality()),
                record.hasSensorTime() ? Optional.of(toInstant(record.getSensorTime())) : Optional.empty(),
                record.getSensorSeq(),
                record.getQuality(),
                record.getCorrelationId());
    }

    static Modality toModality(SensorModality modality) {
        return switch (modality) {
            case MODALITY_RF -> Modality.RF;
            case MODALITY_RADAR -> Modality.RADAR;
            case MODALITY_EO_IR -> Modality.EO_IR;
            case MODALITY_ACOUSTIC -> Modality.ACOUSTIC;
            case MODALITY_GNSS -> Modality.GNSS;
            case MODALITY_EXTERNAL -> Modality.EXTERNAL;
            case MODALITY_UNSPECIFIED, UNRECOGNIZED -> Modality.UNSPECIFIED;
        };
    }

    static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
