package pk.nust.cuas.reporting.api.rest;

/** Raised when a report job does not exist. Rendered as 404. */
public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(String message) {
        super(message);
    }
}
