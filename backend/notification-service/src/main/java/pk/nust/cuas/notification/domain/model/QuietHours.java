package pk.nust.cuas.notification.domain.model;

import java.time.LocalTime;
import java.util.Optional;

/**
 * A recipient's quiet-hours window, during which non-critical notifications are suppressed (section
 * 4.1.9).
 *
 * <p>The window may wrap midnight — {@code 22:00}–{@code 06:00} is quiet across the night. An absent
 * start or end means no quiet hours at all. Evaluated against a wall-clock time; the recipient's time
 * zone handling is a later refinement, so the times are interpreted in the platform clock's zone.
 *
 * @param start when quiet hours begin, or empty for none
 * @param end   when quiet hours end
 */
public record QuietHours(Optional<LocalTime> start, Optional<LocalTime> end) {

    public QuietHours {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end must not be null; use Optional.empty()");
        }
        if (start.isPresent() != end.isPresent()) {
            throw new IllegalArgumentException("quiet hours need both a start and an end, or neither");
        }
    }

    public static QuietHours none() {
        return new QuietHours(Optional.empty(), Optional.empty());
    }

    public static QuietHours of(LocalTime start, LocalTime end) {
        return new QuietHours(Optional.of(start), Optional.of(end));
    }

    /**
     * @param time a wall-clock time
     * @return whether {@code time} falls within the quiet window (empty window is never quiet)
     */
    public boolean contains(LocalTime time) {
        if (start.isEmpty()) {
            return false;
        }
        LocalTime from = start.get();
        LocalTime to = end.get();
        if (from.equals(to)) {
            return false;                                   // a zero-width window is never quiet
        }
        if (from.isBefore(to)) {
            return !time.isBefore(from) && time.isBefore(to);
        }
        // Wraps midnight: quiet if at/after the start OR before the end.
        return !time.isBefore(from) || time.isBefore(to);
    }
}
