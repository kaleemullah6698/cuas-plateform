package pk.nust.cuas.track.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AxisTest {

    private static double[][] covariance(double seed) {
        return new double[][] {{seed, 0, 0}, {0, seed * 2, 0}, {0, 0, seed * 3}};
    }

    @Test
    void twoAxesWithTheSameNumbersAreEqualEvenThoughTheirArraysAreDifferentObjects() {
        // A record's generated equals compares an array component by reference, so without the
        // override every round trip through the database or the Redis cache would look like a
        // different state — and any check that a filter resumed where it left off would be vacuous.
        Axis a = new Axis(1.5, 2.5, 3.5, covariance(4.0));
        Axis b = new Axis(1.5, 2.5, 3.5, covariance(4.0));

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void axesDifferingOnlyInTheirCovarianceAreNotEqual() {
        Axis a = new Axis(1.5, 2.5, 3.5, covariance(4.0));
        Axis b = new Axis(1.5, 2.5, 3.5, covariance(9.0));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void axesDifferingInTheirMeanAreNotEqual() {
        assertThat(new Axis(1.5, 2.5, 3.5, covariance(4.0)))
                .isNotEqualTo(new Axis(1.5, 2.5, 9.9, covariance(4.0)));
    }

    @Test
    void theCovarianceIsCopiedInAndOutSoAStoredAxisCannotBeMutated() {
        double[][] source = covariance(4.0);
        Axis axis = new Axis(1.0, 2.0, 3.0, source);

        source[0][0] = 999.0;                 // mutate what was passed in
        axis.covariance()[1][1] = 888.0;      // mutate what was handed out

        assertThat(axis.covariance()[0][0]).isEqualTo(4.0);
        assertThat(axis.covariance()[1][1]).isEqualTo(8.0);
    }

    @Test
    void toStringShowsTheCovarianceContentsRatherThanAnArrayIdentity() {
        assertThat(new Axis(1.0, 2.0, 3.0, covariance(4.0)).toString()).contains("4.0", "8.0", "12.0");
    }

    @Test
    void aCovarianceThatIsNotThreeByThreeIsRejected() {
        assertThatThrownBy(() -> new Axis(1, 2, 3, new double[][] {{1, 2}, {3, 4}}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
