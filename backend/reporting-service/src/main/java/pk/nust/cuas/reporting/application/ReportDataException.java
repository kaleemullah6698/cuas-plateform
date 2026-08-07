package pk.nust.cuas.reporting.application;

import pk.nust.cuas.reporting.domain.model.ReportFailureReason;

/**
 * Thrown by a generator when a report cannot be produced from the request itself — the parameters are
 * unusable ({@link ReportFailureReason#INVALID_RANGE}) or the query matched nothing
 * ({@link ReportFailureReason#NO_DATA}). Both are the caller's to fix, and both fail the job
 * terminally rather than being retried.
 */
public class ReportDataException extends RuntimeException {

    private final transient ReportFailureReason reason;

    public ReportDataException(ReportFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ReportFailureReason reason() {
        return reason;
    }

    public static ReportDataException noData(String message) {
        return new ReportDataException(ReportFailureReason.NO_DATA, message);
    }

    public static ReportDataException invalidRange(String message) {
        return new ReportDataException(ReportFailureReason.INVALID_RANGE, message);
    }
}
