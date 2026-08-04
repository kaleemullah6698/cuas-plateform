package pk.nust.cuas.ingestion.api.dto;

import java.util.List;

/**
 * One rejected element of an ingest batch, reported so the adapter can correct and resend just that
 * detection rather than the whole batch.
 *
 * @param index      the zero-based position of the detection in the request
 * @param sensorSeq  the sensor sequence number, when it could be read; {@code 0} if the record could
 *                   not be parsed
 * @param violations the reasons the detection was rejected
 */
public record RejectedDetection(int index, long sensorSeq, List<String> violations) {

    public RejectedDetection {
        violations = List.copyOf(violations);
    }
}
