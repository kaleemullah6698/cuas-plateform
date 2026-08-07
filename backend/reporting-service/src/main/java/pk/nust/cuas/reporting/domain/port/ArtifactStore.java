package pk.nust.cuas.reporting.domain.port;

import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.reporting.domain.model.ArtifactRef;
import pk.nust.cuas.reporting.domain.model.ReportFormat;

/**
 * Stores and retrieves rendered report artifacts (ARCHITECTURE.md §4.1.10).
 *
 * <p>A filesystem adapter serves development; MinIO — the platform's content-addressed object store
 * (§6.6) — is the production swap behind this port, nothing above it changing. The store computes the
 * content hash that makes an exported report verifiable.
 */
public interface ArtifactStore {

    /**
     * Stores a rendered report and returns its reference.
     *
     * @param reportId the job the artifact belongs to
     * @param format   the export format
     * @param content  the rendered bytes
     * @param rowCount the number of source records summarised
     * @return the artifact reference (uri, sha256, size, rows)
     */
    ArtifactRef store(UUID reportId, ReportFormat format, byte[] content, long rowCount);

    /** Loads the artifact bytes for download, verifying they still match the reference's hash. */
    Optional<byte[]> load(ArtifactRef artifact);
}
