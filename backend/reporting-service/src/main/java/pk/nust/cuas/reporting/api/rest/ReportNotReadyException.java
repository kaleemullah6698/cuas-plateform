package pk.nust.cuas.reporting.api.rest;

/**
 * Raised when a report's artifact is requested but the job has not succeeded — it is still running, or
 * it failed. Rendered as 409: the request is valid but the job's state cannot satisfy it yet.
 */
public class ReportNotReadyException extends RuntimeException {

    public ReportNotReadyException(String message) {
        super(message);
    }
}
