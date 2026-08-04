package pk.nust.cuas.track.domain.model;

/**
 * The geographic sector a track belongs to (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Sector partitioning is the service's scaling model: an instance owns a sector exclusively. The
 * foundation assigns each track a sector and scopes association to it, which is the correctness half
 * of that model; the exclusive per-instance ownership and sharding are the scaling half, deferred
 * (M5.2+). A value object rather than a bare string so the concept is named and cannot be confused
 * with any other identifier.
 *
 * @param value the sector identifier
 */
public record SectorId(String value) {

    public SectorId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sector id must not be blank");
        }
    }
}
