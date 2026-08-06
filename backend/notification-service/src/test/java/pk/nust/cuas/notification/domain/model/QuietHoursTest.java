package pk.nust.cuas.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** The window arithmetic of {@link QuietHours}, including the midnight wrap. */
class QuietHoursTest {

    @Test
    void anAbsentWindowIsNeverQuiet() {
        assertThat(QuietHours.none().contains(LocalTime.MIDNIGHT)).isFalse();
        assertThat(QuietHours.none().contains(LocalTime.NOON)).isFalse();
    }

    @Test
    void aSameDayWindowContainsTimesBetweenItsBounds() {
        QuietHours window = QuietHours.of(LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThat(window.contains(LocalTime.of(12, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(8, 59))).isFalse();
        assertThat(window.contains(LocalTime.of(17, 0))).isFalse();  // end is exclusive
    }

    @Test
    void aWindowThatWrapsMidnightIsQuietAcrossTheNight() {
        QuietHours window = QuietHours.of(LocalTime.of(22, 0), LocalTime.of(6, 0));

        assertThat(window.contains(LocalTime.of(23, 30))).isTrue();
        assertThat(window.contains(LocalTime.of(2, 0))).isTrue();
        assertThat(window.contains(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void aZeroWidthWindowIsNeverQuiet() {
        assertThat(QuietHours.of(LocalTime.of(9, 0), LocalTime.of(9, 0)).contains(LocalTime.of(9, 0)))
                .isFalse();
    }

    @Test
    void aWindowNeedsBothBoundsOrNeither() {
        assertThatThrownBy(() -> new QuietHours(java.util.Optional.of(LocalTime.NOON),
                java.util.Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
