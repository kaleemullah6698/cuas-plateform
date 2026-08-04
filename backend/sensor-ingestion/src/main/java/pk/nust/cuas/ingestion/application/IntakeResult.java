package pk.nust.cuas.ingestion.application;

import java.util.List;

/**
 * The outcome of accepting one detection at the intake boundary (REST or gRPC).
 *
 * <p>Intake validates the header and, if it passes, publishes the record to the raw topic; the
 * heavier normalisation happens asynchronously on the consumer side. So this result reports only
 * whether the record was accepted for the pipeline, not whether it was ultimately normalised.
 *
 * @param accepted   whether the record was published to the raw topic
 * @param sensorSeq  the sensor sequence number, echoed so a streaming caller can correlate the ack
 * @param violations the validation failures when {@code accepted} is false, otherwise empty
 */
public record IntakeResult(boolean accepted, long sensorSeq, List<String> violations) {

    public IntakeResult {
        violations = List.copyOf(violations);
    }

    static IntakeResult accepted(long sensorSeq) {
        return new IntakeResult(true, sensorSeq, List.of());
    }

    static IntakeResult rejected(long sensorSeq, List<String> violations) {
        return new IntakeResult(false, sensorSeq, violations);
    }
}
