package pk.nust.cuas.reporting.domain.model;

/**
 * Why a report generation failed (ARCHITECTURE.md §4.1.10). Domain mirror of the contract enum.
 *
 * <p>The distinction that matters is whether the request was impossible ({@link #NO_DATA},
 * {@link #INVALID_RANGE} — the caller's to fix) or the platform faulted ({@link #SOURCE_ERROR},
 * {@link #RENDER_ERROR}).
 */
public enum ReportFailureReason {
    NO_DATA,
    INVALID_RANGE,
    SOURCE_ERROR,
    RENDER_ERROR
}
