package pk.nust.cuas.alert.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.alert.domain.exception.AlertNotFoundException;
import pk.nust.cuas.alert.domain.exception.IllegalAlertTransitionException;
import pk.nust.cuas.alert.domain.model.Acknowledgement;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.DedupKey;
import pk.nust.cuas.alert.domain.model.Escalation;
import pk.nust.cuas.alert.domain.model.EscalationTrigger;
import pk.nust.cuas.alert.domain.model.Resolution;
import pk.nust.cuas.alert.domain.policy.EscalationPolicy;
import pk.nust.cuas.alert.domain.policy.SuppressionPolicy;
import pk.nust.cuas.alert.domain.port.AlertEventPublisher;
import pk.nust.cuas.alert.domain.port.AlertRepository;
import pk.nust.cuas.alert.domain.port.EscalationScheduler;
import pk.nust.cuas.alert.domain.service.AlertRaisingPolicy;
import pk.nust.cuas.alert.domain.service.ThreatTransition;
import pk.nust.cuas.commons.id.Uuidv7Generator;

/**
 * The alert lifecycle (ARCHITECTURE.md section 4.1.8): raise, deduplicate, acknowledge, assign,
 * escalate, resolve.
 *
 * <p><strong>Deduplication is defended twice, and the second defence is the real one.</strong> The
 * read below catches the common case cheaply; the unique constraint in {@code alertdb} catches the
 * case the read cannot — two instances processing the same threat transition after a consumer group
 * rebalance, both seeing no open alert, both inserting. Section 5.7 names duplicate alerts as the
 * failure that destroys operator trust faster than any other, and a check that only works when
 * nothing is racing is not a defence against it.
 */
@Service
public class AlertLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AlertLifecycleService.class);

    private final AlertRepository alerts;
    private final AlertEventPublisher events;
    private final EscalationScheduler scheduler;
    private final AlertRaisingPolicy raising;
    private final SuppressionPolicy suppression;
    private final EscalationPolicy escalation;
    private final AlertReferenceGenerator references;
    private final Uuidv7Generator ids;
    private final Clock clock;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AlertLifecycleService(AlertRepository alerts, AlertEventPublisher events,
                                 EscalationScheduler scheduler, AlertRaisingPolicy raising,
                                 SuppressionPolicy suppression, EscalationPolicy escalation,
                                 AlertReferenceGenerator references, Uuidv7Generator ids,
                                 Clock clock) {
        this.alerts = alerts;
        this.events = events;
        this.scheduler = scheduler;
        this.raising = raising;
        this.suppression = suppression;
        this.escalation = escalation;
        this.references = references;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Handles a threat level change from {@code threat.level.changed.v1}.
     *
     * @param transition the change
     * @return the alert raised, or empty when the transition raised nothing — a de-escalation, a
     *         level below the raising threshold, or a recurrence folded into an open alert
     */
    @Transactional
    public Optional<Alert> onThreatTransition(ThreatTransition transition) {
        if (!raising.shouldRaise(transition)) {
            log.debug("Track {} moved to {} — below the raising threshold or a de-escalation",
                    transition.trackId(), transition.currentLevel());
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        DedupKey key = new DedupKey(
                transition.trackId(), transition.currentLevel(), transition.policyVersion());

        Optional<Alert> open = alerts.findOpenByDedupKey(key);
        if (open.isPresent() && suppression.suppresses(open.get(), now)) {
            Alert existing = open.get();
            existing.recordRecurrence();
            alerts.save(existing);
            log.debug("Folded a recurrence into alert {} (now {} occurrences)",
                    existing.reference(), existing.occurrenceCount());
            return Optional.empty();
        }

        Alert alert = Alert.raise(
                ids.generate(now),
                references.nextAlertReference(now),
                key,
                transition.trackId(),
                transition.sectorId(),
                transition.assessmentId(),
                transition.currentLevel(),
                transition.recommendation(),
                transition.currentScore(),
                title(transition),
                summary(transition),
                now,
                escalation.nextEscalationAt(transition.currentLevel(), 0, now).orElse(null),
                transition.correlationId());

        if (!alerts.raise(alert)) {
            // Another instance won the race. It has raised the alert and published the event; doing
            // either again is precisely the duplicate section 5.7 forbids.
            log.debug("Alert for dedup key {} was raised concurrently; skipping", key.value());
            return Optional.empty();
        }

        alert.escalatesAt().ifPresent(due -> scheduler.schedule(alert.alertId(), due));
        events.publishRaised(alert);
        log.info("Raised {} for track {} at {} (score {})",
                alert.reference(), alert.trackId(), alert.severity(), alert.threatScore());
        return Optional.of(alert);
    }

    /**
     * Acknowledges an alert, stopping its escalation timer.
     *
     * @param alertId   the alert
     * @param actorId   the acknowledging user
     * @param actorName their display name
     * @param note      optional free text
     * @return the alert in its new state
     */
    @Transactional
    public Alert acknowledge(UUID alertId, String actorId, String actorName, String note) {
        Alert alert = require(alertId);
        Instant now = Instant.now(clock);
        transition(alert, () -> alert.acknowledge(now));

        Acknowledgement acknowledgement =
                new Acknowledgement(alertId, actorId, actorName, note, now);
        alerts.save(alert);
        alerts.recordAcknowledgement(acknowledgement);
        scheduler.cancel(alertId);
        events.publishAcknowledged(alert, acknowledgement);
        log.info("{} acknowledged by {}", alert.reference(), actorId);
        return alert;
    }

    /**
     * Assigns an alert without acknowledging it.
     *
     * <p>Deliberately separate: assignment says "this is yours", acknowledgement says "I have seen
     * it", and only the second is evidence anybody looked. Assigning does not stop the escalation
     * timer, because handing work to someone who has not yet read it is not attending to it.
     *
     * @param alertId    the alert
     * @param assigneeId the user it is assigned to
     * @return the alert in its new state
     */
    @Transactional
    public Alert assign(UUID alertId, String assigneeId) {
        Alert alert = require(alertId);
        transition(alert, () -> alert.assignTo(assigneeId));
        alerts.save(alert);
        log.info("{} assigned to {}", alert.reference(), assigneeId);
        return alert;
    }

    /**
     * Resolves an alert with a disposition.
     *
     * @param alertId     the alert
     * @param disposition the verdict
     * @param note        optional free text
     * @param actorId     the resolving user
     * @param actorName   their display name
     * @return the alert in its new state
     */
    @Transactional
    public Alert resolve(UUID alertId, AlertDisposition disposition, String note,
                         String actorId, String actorName) {
        Alert alert = require(alertId);
        Instant now = Instant.now(clock);
        transition(alert, () -> alert.resolve(now));

        Resolution resolution =
                new Resolution(alertId, disposition, note, actorId, actorName, false, now);
        alerts.save(alert);
        alerts.recordResolution(resolution);
        scheduler.cancel(alertId);
        events.publishResolved(alert, resolution);
        log.info("{} resolved by {} as {}", alert.reference(), actorId, disposition);
        return alert;
    }

    /**
     * Escalates an alert because its deadline passed, or because an operator asked.
     *
     * @param alertId the alert
     * @param trigger why
     * @param actorId the operator, or null when the scheduler fired it
     * @return the alert in its new state, or empty when it was no longer escalatable — acknowledged
     *         or resolved between the sweep reading it and this call. That is the ordinary outcome
     *         of a race with an operator, not a fault, so it is reported rather than thrown.
     */
    @Transactional
    public Optional<Alert> escalate(UUID alertId, EscalationTrigger trigger, String actorId) {
        Optional<Alert> found = alerts.findById(alertId);
        if (found.isEmpty() || !found.get().status().isEscalatable()) {
            scheduler.cancel(alertId);
            return Optional.empty();
        }
        Alert alert = found.get();
        Instant now = Instant.now(clock);
        int previousLevel = alert.escalationLevel();
        Instant nextAt = escalation
                .nextEscalationAt(alert.severity(), previousLevel + 1, now)
                .orElse(null);

        alert.escalate(now, nextAt);
        Escalation record = new Escalation(alertId, previousLevel, alert.escalationLevel(),
                trigger, actorId, now, nextAt);

        alerts.save(alert);
        alerts.recordEscalation(record);
        if (nextAt == null) {
            // The ladder is exhausted. The alert stays open and stays visible; it simply stops
            // paging, because an escalation nobody is going to act on trains operators to mute.
            scheduler.cancel(alertId);
        } else {
            scheduler.schedule(alertId, nextAt);
        }
        events.publishEscalated(alert, record);
        log.info("{} escalated to level {} ({})",
                alert.reference(), alert.escalationLevel(), trigger);
        return Optional.of(alert);
    }

    /**
     * Closes every open alert about a track that no longer exists — it was lost or merged.
     *
     * <p>Auto-resolved and {@code INCONCLUSIVE}: the system knows the track ended, not whether the
     * alert had been right, and recording a verdict it cannot support would poison the calibration
     * statistic dispositions exist to produce.
     *
     * @param trackId the track
     * @param reason  why, for the note
     * @return the alerts closed
     */
    @Transactional
    public List<Alert> closeAlertsFor(UUID trackId, String reason) {
        Instant now = Instant.now(clock);
        List<Alert> open = alerts.findOpenByTrackId(trackId);
        for (Alert alert : open) {
            alert.resolve(now);
            Resolution resolution = Resolution.automatic(alert.alertId(), reason, now);
            alerts.save(alert);
            alerts.recordResolution(resolution);
            scheduler.cancel(alert.alertId());
            events.publishResolved(alert, resolution);
        }
        if (!open.isEmpty()) {
            log.info("Auto-resolved {} alert(s) for track {}: {}", open.size(), trackId, reason);
        }
        return open;
    }

    private Alert require(UUID alertId) {
        return alerts.findById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
    }

    /**
     * Runs an aggregate transition, translating its state guard into the API's conflict response.
     * The rule stays on the aggregate; only its presentation belongs here.
     */
    private void transition(Alert alert, Runnable change) {
        try {
            change.run();
        } catch (IllegalStateException ex) {
            throw new IllegalAlertTransitionException(alert.reference(), ex.getMessage());
        }
    }

    private static String title(ThreatTransition transition) {
        return transition.currentLevel() + " — track " + transition.trackId()
                + " in sector " + transition.sectorId();
    }

    private static String summary(ThreatTransition transition) {
        String from = transition.previousLevel().map(Enum::name).orElse("unassessed");
        return "Threat level " + from + " to " + transition.currentLevel()
                + " (composite " + String.format("%.3f", transition.currentScore())
                + " under " + transition.policyVersion() + "). Advisory: "
                + transition.recommendation() + ".";
    }
}
