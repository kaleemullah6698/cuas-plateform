package pk.nust.cuas.reporting.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@code report_artifacts} — the stored artifact reference of a succeeded job. */
@Entity
@Table(name = "report_artifacts")
public class ReportArtifactEntity {

    @Id
    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "uri", nullable = false)
    private String uri;

    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "format", nullable = false)
    private String format;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportArtifactEntity() {
    }

    public ReportArtifactEntity(UUID reportId, String uri, String sha256, long sizeBytes, String format,
                                Instant createdAt) {
        this.reportId = reportId;
        this.uri = uri;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.format = format;
        this.createdAt = createdAt;
    }

    public String uri() {
        return uri;
    }

    public String sha256() {
        return sha256;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String format() {
        return format;
    }
}
