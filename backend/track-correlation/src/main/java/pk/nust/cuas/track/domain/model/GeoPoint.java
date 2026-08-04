package pk.nust.cuas.track.domain.model;

/**
 * A geodetic position — the domain's own position type, mapped from the canonical
 * {@code detection.v1.GeoPosition} at the boundary so the domain stays free of the generated types.
 *
 * @param latitude   degrees, WGS-84
 * @param longitude  degrees, WGS-84
 * @param altitudeM  metres above mean sea level
 */
public record GeoPoint(double latitude, double longitude, double altitudeM) {

    private static final double EARTH_RADIUS_M = 6_371_000.0d;

    /**
     * Great-circle (haversine) distance to another point, in metres.
     *
     * <p>Horizontal only: altitude is not folded in. For association gating at the ranges a
     * counter-UAS system works over, the horizontal separation is what the gate is about, and a 2-D
     * distance keeps the foundation's association logic simple and inspectable. A full 3-D metric
     * arrives with the Kalman state in M5.2.
     *
     * @param other the point to measure to
     * @return the horizontal distance in metres
     */
    public double distanceTo(GeoPoint other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }
}
