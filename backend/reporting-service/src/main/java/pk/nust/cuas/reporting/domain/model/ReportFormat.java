package pk.nust.cuas.reporting.domain.model;

/** The export formats the service supports (ARCHITECTURE.md §4.1.10). */
public enum ReportFormat {
    PDF("application/pdf", "pdf"),
    JSON("application/json", "json"),
    CSV("text/csv", "csv");

    private final String contentType;
    private final String extension;

    ReportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
