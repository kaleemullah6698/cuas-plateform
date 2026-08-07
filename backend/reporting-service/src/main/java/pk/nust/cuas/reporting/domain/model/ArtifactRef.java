package pk.nust.cuas.reporting.domain.model;

/**
 * A reference to a rendered report artifact (ARCHITECTURE.md §4.1.10): where it is stored and what
 * makes it verifiable. The artifact bytes are never held on the job or on the wire — only this
 * reference is, the same rule §5.3 sets for evidence.
 *
 * @param uri       the artifact-store URI
 * @param sha256    the content hash, so an exported report stays verifiable
 * @param sizeBytes the artifact size
 * @param rowCount  the number of source records the report summarises
 * @param format    the export format
 */
public record ArtifactRef(String uri, String sha256, long sizeBytes, long rowCount, ReportFormat format) {

    public ArtifactRef {
        if (uri == null || sha256 == null || format == null) {
            throw new IllegalArgumentException("uri, sha256 and format are required");
        }
    }
}
