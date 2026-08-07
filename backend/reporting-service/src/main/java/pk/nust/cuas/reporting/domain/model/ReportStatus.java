package pk.nust.cuas.reporting.domain.model;

/**
 * The lifecycle of a report job (ARCHITECTURE.md §4.1.10): accepted ({@code PENDING}), being built
 * ({@code RUNNING}), then {@code SUCCEEDED} with an artifact or {@code FAILED} with a reason.
 * Generation never blocks the request thread — the 202 returns a {@code PENDING} job.
 */
public enum ReportStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
