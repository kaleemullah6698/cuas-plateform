package pk.nust.cuas.alert.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.domain.port.IncidentRepository;

/**
 * Produces the operator-facing handles section 2.4.3 renders: {@code ALT-9912},
 * {@code INC-2026-0087}.
 *
 * <p><strong>These are labels, not identities.</strong> Every alert and incident is identified by a
 * UUIDv7; the reference exists because an operator has to say one out loud on a radio, and
 * "018f2b3c-4d5e-7f80" is not sayable. Nothing joins on a reference and nothing looks one up by it,
 * which is what keeps the generation scheme free to be simple.
 *
 * <p>Alert references are drawn from a per-process counter seeded from the clock. Two instances can
 * therefore mint the same string, and that is an accepted consequence rather than an oversight: the
 * alternative is a database sequence on the hot path of every raise, bought for a label whose only
 * job is to be short. Incident references are different — they are quoted in reports and cited
 * across shifts — so those are numbered from a count of the incidents actually declared this year.
 */
@Component
public class AlertReferenceGenerator {

    private static final String ALERT_PREFIX = "ALT-";
    private static final String INCIDENT_PREFIX = "INC-";
    private static final int ALERT_SEQUENCE_MODULUS = 10_000;

    private final IncidentRepository incidents;
    private final AtomicLong alertSequence;

    public AlertReferenceGenerator(IncidentRepository incidents, Clock clock) {
        this.incidents = incidents;
        // Seeded from the epoch second so a restart does not reuse the labels of the last few
        // minutes, which is the only collision an operator would actually notice.
        this.alertSequence = new AtomicLong(Instant.now(clock).getEpochSecond());
    }

    /**
     * @param at platform clock
     * @return the next alert handle, e.g. {@code ALT-9912}
     */
    public String nextAlertReference(Instant at) {
        long next = Math.floorMod(alertSequence.incrementAndGet(), ALERT_SEQUENCE_MODULUS);
        return ALERT_PREFIX + String.format("%04d", next);
    }

    /**
     * @param at platform clock
     * @return the next incident handle, e.g. {@code INC-2026-0087}
     */
    public String nextIncidentReference(Instant at) {
        int year = at.atZone(ZoneOffset.UTC).getYear();
        long ordinal = incidents.countDeclaredIn(year) + 1;
        return INCIDENT_PREFIX + year + "-" + String.format("%04d", ordinal);
    }
}
