package pk.nust.cuas.track.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * The constant-acceleration Kalman state for one spatial axis: the estimated position, velocity, and
 * acceleration, and the 3×3 covariance expressing how uncertain that estimate is.
 *
 * <p>Immutable: the covariance is defensively copied in and out, so a stored {@code Axis} cannot be
 * mutated behind the aggregate's back. The Kalman filter always produces a new {@code Axis} rather
 * than editing one in place.
 *
 * @param position     estimated position along the axis, metres from the track origin
 * @param velocity     estimated velocity, m/s
 * @param acceleration estimated acceleration, m/s²
 * @param covariance   the 3×3 estimate covariance, ordered [position, velocity, acceleration]
 */
public record Axis(double position, double velocity, double acceleration, double[][] covariance) {

    public Axis {
        if (covariance == null || covariance.length != 3
                || covariance[0].length != 3 || covariance[1].length != 3 || covariance[2].length != 3) {
            throw new IllegalArgumentException("covariance must be a 3x3 matrix");
        }
        covariance = deepCopy(covariance);
    }

    /** @return a copy of the covariance, so callers cannot mutate the stored matrix */
    @Override
    public double[][] covariance() {
        return deepCopy(covariance);
    }

    /** @return the estimate mean as a length-3 vector [position, velocity, acceleration] */
    public double[] mean() {
        return new double[] {position, velocity, acceleration};
    }

    /**
     * Value equality, including the covariance contents.
     *
     * <p>Overridden because a record's generated {@code equals} compares an array component by
     * <em>reference</em>. Two axes carrying identical numbers would then be unequal — which makes
     * every round trip through the database or the Redis cache look like a different state, and
     * would quietly defeat any check that a filter resumed where it left off.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Axis axis
                && Double.compare(position, axis.position) == 0
                && Double.compare(velocity, axis.velocity) == 0
                && Double.compare(acceleration, axis.acceleration) == 0
                && Arrays.deepEquals(covariance, axis.covariance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, velocity, acceleration, Arrays.deepHashCode(covariance));
    }

    @Override
    public String toString() {
        return "Axis[position=" + position + ", velocity=" + velocity
                + ", acceleration=" + acceleration + ", covariance=" + Arrays.deepToString(covariance) + ']';
    }

    private static double[][] deepCopy(double[][] matrix) {
        return new double[][] {
                Arrays.copyOf(matrix[0], 3),
                Arrays.copyOf(matrix[1], 3),
                Arrays.copyOf(matrix[2], 3)};
    }
}
