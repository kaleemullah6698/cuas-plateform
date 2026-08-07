package pk.nust.cuas.reporting.domain.model;

/**
 * The report templates the service ships (ARCHITECTURE.md §4.1.10). Domain mirror of the contract
 * enum; a closed set, because a new report is a new generator and template row, agreed as a change.
 */
public enum ReportType {
    INCIDENT,
    DAILY_SUMMARY,
    SENSOR_PERFORMANCE,
    FUSION_EFFICACY
}
