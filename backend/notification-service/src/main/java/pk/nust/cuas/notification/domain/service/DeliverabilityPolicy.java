package pk.nust.cuas.notification.domain.service;

import java.time.LocalTime;
import pk.nust.cuas.notification.domain.model.RecipientPreferences;
import pk.nust.cuas.notification.domain.model.Urgency;

/**
 * Decides whether a notification should be delivered to a recipient now, or held (section 4.1.9).
 *
 * <p>Two gates, in order. First the recipient's minimum-severity preference: an urgency below it does
 * not reach them at all. Then quiet hours: a non-critical notification arriving during quiet hours is
 * suppressed. A critical notification always delivers — safety outranks preference, so quiet hours
 * never silence a critical alert. Pure, so the decision is tested without a clock or a database.
 */
public final class DeliverabilityPolicy {

    /** The decision for one recipient. */
    public enum Decision {
        /** Deliver now. */
        DELIVER,
        /** Below the recipient's minimum severity — do not notify at all. */
        BELOW_THRESHOLD,
        /** Held by quiet hours. */
        QUIET_HOURS
    }

    /**
     * @param preferences the recipient's preferences
     * @param urgency     the notification's urgency
     * @param now         the current wall-clock time
     * @return whether to deliver, and why not if not
     */
    public Decision evaluate(RecipientPreferences preferences, Urgency urgency, LocalTime now) {
        if (!preferences.accepts(urgency)) {
            return Decision.BELOW_THRESHOLD;
        }
        if (!urgency.isCritical() && preferences.quietHours().contains(now)) {
            return Decision.QUIET_HOURS;
        }
        return Decision.DELIVER;
    }
}
