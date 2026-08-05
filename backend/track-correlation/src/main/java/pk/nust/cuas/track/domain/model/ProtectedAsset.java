package pk.nust.cuas.track.domain.model;

/**
 * A site this system exists to protect — the thing a track's closest point of approach is measured
 * against (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Deliberately minimal: identity, a human-readable name for the operator's screen, and a
 * position. Anything richer — protection radius, priority, geometry beyond a point — belongs to the
 * Configuration Service's asset schema (section 4.1.12) and is not needed to compute a CPA. This
 * service holds only what it uses.
 *
 * @param assetId  stable identifier, carried on the published CPA so a consumer can join back
 * @param name     what the operator sees, e.g. "North Gate"
 * @param position the asset's location
 */
public record ProtectedAsset(String assetId, String name, GeoPoint position) {

    public ProtectedAsset {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("assetId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (position == null) {
            throw new IllegalArgumentException("position is required");
        }
    }
}
