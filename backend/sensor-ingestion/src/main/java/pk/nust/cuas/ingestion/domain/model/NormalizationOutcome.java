package pk.nust.cuas.ingestion.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The result of normalising one raw detection — a closed set of three outcomes.
 *
 * <p>Sealed because the pipeline must handle exactly these cases and no other: a record is either
 * accepted and enriched, seen before, or invalid. Pattern matching over this type is exhaustive,
 * so a new outcome cannot be added without the compiler forcing every handler to consider it.
 */
public sealed interface NormalizationOutcome {

    /**
     * The record is accepted. Carries the platform-assigned identity and ordering fields to stamp
     * onto the canonical record before it is published to {@code sensor.detection.normalized.v1}.
     *
     * @param detectionId  the platform-assigned UUIDv7 (time-sortable)
     * @param ingestTime   the authoritative platform clock at normalisation
     * @param clockDriftMs the measured sensor-clock correction (section 5.3)
     */
    record Normalized(UUID detectionId, Instant ingestTime, long clockDriftMs)
            implements NormalizationOutcome {
    }

    /** The {@code (sensorId, sensorSeq)} pair has already been seen inside the dedup window. */
    record Duplicate() implements NormalizationOutcome {
    }

    /**
     * The record failed validation and is not published. The violations are for logging and
     * telemetry; they are never reflected verbatim to an untrusted caller.
     *
     * @param violations the human-readable reasons, never empty
     */
    record Invalid(List<String> violations) implements NormalizationOutcome {
        public Invalid {
            violations = List.copyOf(violations);
            if (violations.isEmpty()) {
                throw new IllegalArgumentException("an Invalid outcome must carry at least one violation");
            }
        }
    }
}
