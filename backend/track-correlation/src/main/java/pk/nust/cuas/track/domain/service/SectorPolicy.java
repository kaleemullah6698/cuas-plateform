package pk.nust.cuas.track.domain.service;

import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.SectorId;

/**
 * Assigns a position to a geographic sector (ARCHITECTURE.md section 4.1.5).
 *
 * <p>The foundation tiles the world into a fixed grid of square cells and names the sector by its
 * cell: two positions in the same cell share a sector, and association only ever compares detections
 * within one sector. This realises the correctness half of the sector model — scoping association —
 * without the operational half, the exclusive per-instance ownership and sharding, which is scaling
 * work deferred to M5.2. Grid cells have a known weakness at their boundaries (two nearby positions
 * on opposite sides land in different sectors); a coarse cell keeps that rare, and a proper sector
 * geometry is a later refinement.
 */
public final class SectorPolicy {

    private final double cellDegrees;

    /**
     * @param cellDegrees the side of each square sector cell, in degrees of latitude/longitude
     * @throws IllegalArgumentException when the cell size is not positive
     */
    public SectorPolicy(double cellDegrees) {
        if (!(cellDegrees > 0.0d)) {
            throw new IllegalArgumentException("sector cell size must be positive, was " + cellDegrees);
        }
        this.cellDegrees = cellDegrees;
    }

    /**
     * @param position the position to place
     * @return the sector its grid cell names
     */
    public SectorId assign(GeoPoint position) {
        long latCell = (long) Math.floor(position.latitude() / cellDegrees);
        long lonCell = (long) Math.floor(position.longitude() / cellDegrees);
        return new SectorId("SECTOR_" + latCell + '_' + lonCell);
    }
}
