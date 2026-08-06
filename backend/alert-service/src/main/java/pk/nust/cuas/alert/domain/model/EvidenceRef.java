package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A reference to one item in an incident's evidence bundle (ARCHITECTURE.md section 2.4.3).
 *
 * <p><strong>A reference, never a payload.</strong> The same rule section 5.3 applies to IQ captures
 * and video frames: the object lives in the evidence store and this carries its address. An event or
 * a row holding a spectrogram would put megabytes onto a topic the gateway, reporting and audit all
 * consume, and into a database sized for metadata.
 *
 * <p><strong>{@code sha256} is what makes an exported bundle verifiable after it leaves the
 * platform.</strong> A bundle handed to a reviewer months later is only evidence if they can confirm
 * the frame they are looking at is the frame the system recorded; a URI alone proves nothing once
 * the object is out of the store's custody.
 *
 * @param evidenceId  UUIDv7
 * @param incidentId  the investigation
 * @param kind        what it is
 * @param uri         the evidence store address — never an inline payload
 * @param sha256      content hash of the referenced object, 64 lowercase hex characters
 * @param sizeBytes   the object's size
 * @param contentType its media type
 * @param sourceId    the sensor, model or service that produced it
 * @param capturedAt  when it was produced
 */
public record EvidenceRef(
        UUID evidenceId,
        UUID incidentId,
        EvidenceKind kind,
        String uri,
        String sha256,
        long sizeBytes,
        String contentType,
        String sourceId,
        Instant capturedAt) {

    private static final int SHA256_HEX_LENGTH = 64;

    public EvidenceRef {
        if (evidenceId == null || incidentId == null || kind == null || capturedAt == null) {
            throw new IllegalArgumentException(
                    "Evidence needs an id, an incident, a kind and a capture time");
        }
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Evidence must reference a stored object");
        }
        if (sha256 == null || sha256.length() != SHA256_HEX_LENGTH) {
            throw new IllegalArgumentException(
                    "Evidence must carry a SHA-256 of the object it references, or an exported"
                            + " bundle cannot be verified once it leaves the platform");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("Evidence size cannot be negative");
        }
        contentType = contentType == null ? "application/octet-stream" : contentType;
        sourceId = sourceId == null ? "" : sourceId;
    }
}
