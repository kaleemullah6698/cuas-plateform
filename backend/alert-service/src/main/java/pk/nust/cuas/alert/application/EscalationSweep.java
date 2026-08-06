package pk.nust.cuas.alert.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.EscalationTrigger;
import pk.nust.cuas.alert.domain.port.AlertRepository;
import pk.nust.cuas.alert.domain.port.EscalationScheduler;
import pk.nust.cuas.alert.domain.port.LeaderElection;

/**
 * Fires the escalations whose deadline has passed (ARCHITECTURE.md section 4.1.8).
 *
 * <p><strong>Two sources, and the second is the point.</strong> The Redis sorted set answers "which
 * alerts are due?" cheaply and is consulted first. The database is then queried for the same
 * question, and anything it returns that Redis did not is escalated too — and rescheduled. That
 * reconciliation is what makes a lost Redis a performance event rather than a safety one: without
 * it, a cache restart would silently end the escalation of every unacknowledged alert on the board,
 * and the only symptom would be a critical alert that quietly stopped paging.
 *
 * <p><strong>Leader election reduces contention; the aggregate prevents duplicates.</strong> Under a
 * partition two instances can both believe they lead, and no lease over a single Redis prevents it.
 * Each escalation therefore re-reads its alert inside a transaction and applies the transition
 * through the aggregate's own state guard, so a second attempt finds the alert already escalated and
 * changes nothing. The sweep would be correct with no leader at all; leadership just stops every
 * replica doing the work.
 */
@Component
public class EscalationSweep {

    private static final Logger log = LoggerFactory.getLogger(EscalationSweep.class);

    /** How many alerts one sweep will escalate, so a backlog cannot monopolise the scheduler. */
    private static final int BATCH_LIMIT = 200;

    private final AlertLifecycleService lifecycle;
    private final AlertRepository alerts;
    private final EscalationScheduler scheduler;
    private final LeaderElection leader;
    private final Clock clock;

    public EscalationSweep(AlertLifecycleService lifecycle, AlertRepository alerts,
                           EscalationScheduler scheduler, LeaderElection leader, Clock clock) {
        this.lifecycle = lifecycle;
        this.alerts = alerts;
        this.scheduler = scheduler;
        this.leader = leader;
        this.clock = clock;
    }

    /**
     * Runs one sweep.
     *
     * @return how many alerts escalated
     */
    public int sweep() {
        if (!leader.isLeader()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        Set<UUID> due = new LinkedHashSet<>(scheduler.due(now, BATCH_LIMIT));

        // Reconcile against the database. An alert whose deadline is on its row but not in the
        // sorted set has been dropped by a Redis restart or an eviction; escalating it here and
        // rescheduling is what stops that from being permanent and silent.
        List<Alert> missed = alerts.findEscalationsDue(now, BATCH_LIMIT);
        for (Alert alert : missed) {
            if (due.add(alert.alertId())) {
                log.warn("Alert {} was due but absent from the scheduler; recovered from the"
                        + " database", alert.reference());
            }
        }

        int escalated = 0;
        for (UUID alertId : due) {
            try {
                if (lifecycle.escalate(alertId, EscalationTrigger.UNACKNOWLEDGED, null).isPresent()) {
                    escalated++;
                }
            } catch (RuntimeException ex) {
                // One alert must not take the sweep down. Left in the schedule, it is retried on the
                // next pass; a sweep that aborted here would stop every later alert escalating.
                log.error("Escalation failed for alert {}; it stays scheduled for the next sweep",
                        alertId, ex);
            }
        }
        if (escalated > 0) {
            log.info("Escalation sweep fired {} of {} due alert(s)", escalated, due.size());
        }
        return escalated;
    }
}
