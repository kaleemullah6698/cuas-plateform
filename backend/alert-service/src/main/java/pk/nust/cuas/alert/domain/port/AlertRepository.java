package pk.nust.cuas.alert.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.alert.domain.model.Acknowledgement;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.AlertStatus;
import pk.nust.cuas.alert.domain.model.DedupKey;
import pk.nust.cuas.alert.domain.model.Escalation;
import pk.nust.cuas.alert.domain.model.Resolution;
import pk.nust.cuas.alert.domain.model.Severity;

/**
 * Persistence for alerts and their lifecycle history (ARCHITECTURE.md section 4.1.8,
 * {@code alertdb}). Declared in the domain as a port; the JPA implementation is an outward adapter.
 */
public interface AlertRepository {

    /**
     * Stores a newly raised alert.
     *
     * @param alert the alert
     * @return {@code true} when it was stored, {@code false} when an open alert with the same dedup
     *         key already existed — the unique constraint fired because another instance raised it
     *         first. Reported rather than thrown: two instances processing one transition is an
     *         expected consequence of a consumer group rebalancing, not a fault, and section 5.7
     *         makes losing this race quietly the whole point.
     */
    boolean raise(Alert alert);

    /**
     * @param key the deduplication identity
     * @return the open alert with that key, or empty when none is open
     */
    Optional<Alert> findOpenByDedupKey(DedupKey key);

    /**
     * @param alertId the alert
     * @return it, or empty when no such alert exists
     */
    Optional<Alert> findById(UUID alertId);

    /**
     * @param trackId the track
     * @return every open alert about it — what a lost or merged track has to have closed
     */
    List<Alert> findOpenByTrackId(UUID trackId);

    /**
     * Persists a mutated alert: its row, and the history entry explaining the change.
     *
     * @param alert the alert in its new state
     */
    void save(Alert alert);

    /** Records an acknowledgement alongside the alert row it changed. */
    void recordAcknowledgement(Acknowledgement acknowledgement);

    /** Records an escalation alongside the alert row it changed. */
    void recordEscalation(Escalation escalation);

    /** Records a resolution alongside the alert row it changed. */
    void recordResolution(Resolution resolution);

    /**
     * @param alertId the alert
     * @return its acknowledgements, oldest first
     */
    List<Acknowledgement> acknowledgements(UUID alertId);

    /**
     * @param alertId the alert
     * @return its escalations, oldest first
     */
    List<Escalation> escalations(UUID alertId);

    /**
     * Lists alerts for the read API, newest raised first.
     *
     * @param status   filter by status when given
     * @param severity filter by severity when given
     * @param sectorId filter by sector when given
     * @param limit    the maximum number to return
     * @return the matching alerts
     */
    List<Alert> search(Optional<AlertStatus> status, Optional<Severity> severity,
                       Optional<String> sectorId, int limit);

    /**
     * The open alerts whose escalation deadline has passed.
     *
     * <p>The database, not Redis, is the authority here. The scheduler makes finding them cheap; this
     * query is what lets the schedule be rebuilt after a Redis restart, and what stops a lost cache
     * from silently ending an unacknowledged critical alert's escalation.
     *
     * @param deadline the instant to compare against
     * @param limit    the maximum number to return in one sweep
     * @return the due alerts
     */
    List<Alert> findEscalationsDue(Instant deadline, int limit);

    /**
     * @param alertIds the alerts
     * @return them, in no particular order
     */
    List<Alert> findAllById(List<UUID> alertIds);
}
