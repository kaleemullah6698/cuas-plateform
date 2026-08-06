package pk.nust.cuas.alert.domain.port;

import java.util.Optional;

/**
 * Where evidence objects live (ARCHITECTURE.md section 2.4.3, "Export Evidence Bundle").
 *
 * <p>A port because the storage backend is a deployment concern, not a domain one. A filesystem
 * adapter serves development; MinIO — already the platform's content-addressed object store
 * (section 6.6) — is the M8 infrastructure swap, and nothing above this interface changes when it
 * happens. The same shape as track-correlation's {@code ProtectedAssetRegistry}, for the same
 * reason: the domain names what it needs, and a milestone decides where it comes from.
 *
 * <p>Objects are addressed by URI and verified by SHA-256. The store never returns bytes into the
 * domain — an incident holds references, and an export streams from here directly.
 */
public interface EvidenceStore {

    /**
     * @param uri    the object address
     * @param sha256 the content hash recorded when the evidence was attached
     * @return {@code true} when the object exists and still hashes to that value
     */
    boolean verify(String uri, String sha256);

    /**
     * @param uri the object address
     * @return the object's size in bytes, or empty when it does not exist
     */
    Optional<Long> sizeOf(String uri);
}
