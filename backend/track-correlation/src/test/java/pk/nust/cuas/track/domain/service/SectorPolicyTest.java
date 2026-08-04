package pk.nust.cuas.track.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.SectorId;

class SectorPolicyTest {

    private final SectorPolicy policy = new SectorPolicy(1.0);

    @Test
    void positionsInTheSameCellShareASector() {
        SectorId a = policy.assign(new GeoPoint(33.10, 73.20, 0));
        SectorId b = policy.assign(new GeoPoint(33.90, 73.80, 0));

        assertThat(a).isEqualTo(b);
    }

    @Test
    void positionsInDifferentCellsGetDifferentSectors() {
        SectorId a = policy.assign(new GeoPoint(33.5, 73.5, 0));
        SectorId b = policy.assign(new GeoPoint(34.5, 73.5, 0));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void theSectorIsNamedByItsGridCell() {
        assertThat(policy.assign(new GeoPoint(33.6, 73.0, 0)))
                .isEqualTo(new SectorId("SECTOR_33_73"));
    }

    @Test
    void negativeCoordinatesFloorTowardMoreNegativeCells() {
        // floor(-0.5) = -1, not 0 — a position just south of the equator is in cell -1.
        assertThat(policy.assign(new GeoPoint(-0.5, 10.5, 0)))
                .isEqualTo(new SectorId("SECTOR_-1_10"));
    }

    @Test
    void aNonPositiveCellSizeIsRejected() {
        assertThatThrownBy(() -> new SectorPolicy(0.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
