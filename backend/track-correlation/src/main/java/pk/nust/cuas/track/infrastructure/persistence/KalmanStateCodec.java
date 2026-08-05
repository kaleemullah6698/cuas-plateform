package pk.nust.cuas.track.infrastructure.persistence;

import pk.nust.cuas.track.domain.model.Axis;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;

/**
 * Serialises a {@link KalmanState} to and from the {@code filter_state} text column.
 *
 * <p>A flat, comma-separated list of doubles with a version tag: the origin (3), then each axis's
 * mean (3) and 3×3 covariance (9). {@code Double.toString} round-trips exactly, so the decoded state
 * is bit-identical to the encoded one — the filter resumes precisely where it left off. The format is
 * versioned so it can evolve without silently misreading old rows.
 */
final class KalmanStateCodec {

    private static final String VERSION = "v1";
    private static final int AXIS_VALUES = 12;      // 3 mean + 9 covariance
    private static final int TOTAL_VALUES = 3 + 3 * AXIS_VALUES;

    private KalmanStateCodec() {
    }

    static String encode(KalmanState state) {
        StringBuilder out = new StringBuilder(VERSION);
        append(out, state.origin().latitude());
        append(out, state.origin().longitude());
        append(out, state.origin().altitudeM());
        appendAxis(out, state.east());
        appendAxis(out, state.north());
        appendAxis(out, state.up());
        return out.toString();
    }

    static KalmanState decode(String encoded) {
        String[] parts = encoded.split(",");
        if (parts.length != TOTAL_VALUES + 1 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("unrecognised filter_state format");
        }
        double[] v = new double[TOTAL_VALUES];
        for (int i = 0; i < TOTAL_VALUES; i++) {
            v[i] = Double.parseDouble(parts[i + 1]);
        }
        GeoPoint origin = new GeoPoint(v[0], v[1], v[2]);
        return new KalmanState(origin, axis(v, 3), axis(v, 3 + AXIS_VALUES), axis(v, 3 + 2 * AXIS_VALUES));
    }

    private static void appendAxis(StringBuilder out, Axis axis) {
        append(out, axis.position());
        append(out, axis.velocity());
        append(out, axis.acceleration());
        double[][] covariance = axis.covariance();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                append(out, covariance[i][j]);
            }
        }
    }

    private static Axis axis(double[] v, int offset) {
        double[][] covariance = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                covariance[i][j] = v[offset + 3 + i * 3 + j];
            }
        }
        return new Axis(v[offset], v[offset + 1], v[offset + 2], covariance);
    }

    private static void append(StringBuilder out, double value) {
        out.append(',').append(Double.toString(value));
    }
}
