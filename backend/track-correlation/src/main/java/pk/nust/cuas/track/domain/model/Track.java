package pk.nust.cuas.track.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;

/**
 * A track: the trajectory of one airborne object, built by associating successive detections and
 * estimated by a Kalman filter (ARCHITECTURE.md section 4.1.5). The aggregate root of this service.
 *
 * <p>Immutable (CLAUDE.md section 3): every transition — associate, coast, mark lost — returns a new
 * {@code Track} and the lifecycle event, if any, that it produced. The aggregate owns the lifecycle
 * rules; the Kalman arithmetic is delegated to the {@link KalmanFilter} domain service, passed in so
 * the aggregate stays a value with behaviour rather than carrying a service.
 *
 * <p>Two times are tracked, and they diverge while coasting: {@link #lastDetectionAt()} is when a
 * detection last fed the track, and drives the miss timeouts; the {@link #estimate() estimate}'s time
 * is what the current state is valid for, which a coasting prediction advances past the last
 * detection.
 */
public final class Track {

    private final UUID id;
    private final SectorId sector;
    private final TrackStatus status;
    private final KalmanState filter;
    private final TrackState estimate;
    private final int detectionCount;
    private final Instant firstSeen;
    private final Instant lastDetectionAt;
    private final UUID mergedInto;

    private Track(UUID id, SectorId sector, TrackStatus status, KalmanState filter, TrackState estimate,
                  int detectionCount, Instant firstSeen, Instant lastDetectionAt, UUID mergedInto) {
        this.id = id;
        this.sector = sector;
        this.status = status;
        this.filter = filter;
        this.estimate = estimate;
        this.detectionCount = detectionCount;
        this.firstSeen = firstSeen;
        this.lastDetectionAt = lastDetectionAt;
        this.mergedInto = mergedInto;
    }

    /**
     * Opens a new tentative track from a detection that matched no existing track.
     *
     * @param id       the new track's identity (a UUIDv7)
     * @param sector   the sector the initial position falls in
     * @param filter   the initialised Kalman state (origin at the detection's position)
     * @param estimate the initial estimated state (equal to the measurement)
     * @return the new {@code TENTATIVE} track with one detection
     */
    public static Track open(UUID id, SectorId sector, KalmanState filter, TrackState estimate) {
        return new Track(id, sector, TrackStatus.TENTATIVE, filter, estimate, 1,
                estimate.observedAt(), estimate.observedAt(), null);
    }

    /** Reconstitutes a track from storage. Used by the repository; not domain behaviour. */
    public static Track reconstitute(UUID id, SectorId sector, TrackStatus status, KalmanState filter,
                                     TrackState estimate, int detectionCount, Instant firstSeen,
                                     Instant lastDetectionAt) {
        return new Track(id, sector, status, filter, estimate, detectionCount, firstSeen,
                lastDetectionAt, null);
    }

    /** Reconstitutes a merged track from storage, with the survivor it was absorbed into. */
    public static Track reconstituteMerged(UUID id, SectorId sector, KalmanState filter,
                                           TrackState estimate, int detectionCount, Instant firstSeen,
                                           Instant lastDetectionAt, UUID mergedInto) {
        return new Track(id, sector, TrackStatus.MERGED, filter, estimate, detectionCount, firstSeen,
                lastDetectionAt, mergedInto);
    }

    /**
     * Associates a detection: runs the Kalman predict-then-update cycle to the detection's time,
     * advances the count, and applies any lifecycle change — confirming a tentative track that has
     * reached the threshold, or reviving a coasting one.
     *
     * @param measurement  the detection's position
     * @param detectionAt  the detection's ingest time
     * @param detectionId  the detection id
     * @param sensorId     the reporting sensor
     * @param kalman       the filter arithmetic
     * @param confirmation the tentative → confirmed rule
     * @return the advanced track and the lifecycle event it produced, if any
     */
    public Transition associate(GeoPoint measurement, Instant detectionAt, String detectionId,
                                String sensorId, KalmanFilter kalman, TrackConfirmationPolicy confirmation) {
        if (!status.isOpen()) {
            throw new IllegalStateException("cannot associate a detection to a lost track " + id);
        }
        // Advance from the time the filter is currently valid for, not from the last detection: a
        // coasting track's filter has already been predicted past its last detection, and measuring
        // the gap from lastDetectionAt would advance it a second time over the same interval.
        double dt = seconds(estimate.observedAt(), detectionAt);
        KalmanState advanced = kalman.step(filter, dt, measurement);
        TrackState newEstimate = new TrackState(
                kalman.position(advanced), kalman.velocity(advanced), detectionAt, detectionId, sensorId);
        int newCount = detectionCount + 1;

        TrackStatus newStatus = status;
        Optional<TrackLifecycleEvent> event = Optional.empty();
        if (status == TrackStatus.COASTING) {
            newStatus = TrackStatus.CONFIRMED;
            event = Optional.of(TrackLifecycleEvent.transition(TrackStatus.COASTING, TrackStatus.CONFIRMED,
                    "re-associated a detection while coasting", detectionAt));
        } else if (status == TrackStatus.TENTATIVE && confirmation.shouldConfirm(newCount)) {
            newStatus = TrackStatus.CONFIRMED;
            event = Optional.of(TrackLifecycleEvent.transition(TrackStatus.TENTATIVE, TrackStatus.CONFIRMED,
                    "confirmed after " + newCount + " associated detections", detectionAt));
        }
        Track updated = new Track(id, sector, newStatus, advanced, newEstimate, newCount, firstSeen,
                detectionAt, null);
        return new Transition(updated, event);
    }

    /**
     * Coasts a confirmed track that has missed detections: predicts the estimate forward to {@code
     * now} with no measurement, and moves it to {@code COASTING}.
     *
     * @param now    the time to predict to
     * @param kalman the filter arithmetic
     * @return the coasting track and the transition event
     */
    public Transition coast(Instant now, KalmanFilter kalman) {
        if (status != TrackStatus.CONFIRMED) {
            throw new IllegalStateException("only a confirmed track can start coasting, was " + status);
        }
        double dt = seconds(estimate.observedAt(), now);
        KalmanState predicted = kalman.predict(filter, dt);
        TrackState predictedEstimate = new TrackState(
                kalman.position(predicted), kalman.velocity(predicted), now,
                estimate.detectionId(), estimate.sensorId());
        Track coasting = new Track(id, sector, TrackStatus.COASTING, predicted, predictedEstimate,
                detectionCount, firstSeen, lastDetectionAt, null);
        return new Transition(coasting, Optional.of(TrackLifecycleEvent.transition(
                TrackStatus.CONFIRMED, TrackStatus.COASTING, "no detection within the coast timeout", now)));
    }

    /**
     * Marks an open track lost — the terminal state.
     *
     * @param now    when it was declared lost
     * @param reason why
     * @return the lost track and the transition event
     */
    public Transition markLost(Instant now, String reason) {
        if (!status.isOpen()) {
            throw new IllegalStateException("track " + id + " is already lost");
        }
        Track lost = new Track(id, sector, TrackStatus.LOST, filter, estimate, detectionCount,
                firstSeen, lastDetectionAt, null);
        return new Transition(lost, Optional.of(TrackLifecycleEvent.transition(status, TrackStatus.LOST, reason, now)));
    }

    /**
     * Absorbs this track into another: two sensors had independently opened a track on one object,
     * and this is the younger of the pair (ARCHITECTURE.md section 4.1.5).
     *
     * <p>The estimate is left exactly as it was. Re-running the filter over the survivor's
     * detections would mean reprocessing history to produce a state the survivor already holds a
     * better version of — it has been fed continuously, this one has not. The merged track keeps its
     * own final estimate as the historical record of what it believed, and points at the survivor
     * for anything that needs the live answer.
     *
     * @param survivor the track this one is absorbed into — must be a different track
     * @param now      when the merge was decided
     * @return the merged track and the transition event
     * @throws IllegalStateException    when this track is already terminal
     * @throws IllegalArgumentException when asked to merge a track into itself
     */
    public Transition mergeInto(Track survivor, Instant now) {
        if (status.isTerminal()) {
            throw new IllegalStateException("track " + id + " is already " + status);
        }
        if (survivor.id().equals(id)) {
            throw new IllegalArgumentException("a track cannot be merged into itself: " + id);
        }
        Track merged = new Track(id, sector, TrackStatus.MERGED, filter, estimate, detectionCount,
                firstSeen, lastDetectionAt, survivor.id());
        return new Transition(merged, Optional.of(TrackLifecycleEvent.transition(
                status, TrackStatus.MERGED, "merged into track " + survivor.id(), now)));
    }

    /** @return the track this one was absorbed into; present only when the status is MERGED */
    public Optional<UUID> mergedInto() {
        return Optional.ofNullable(mergedInto);
    }

    public UUID id() {
        return id;
    }

    public SectorId sector() {
        return sector;
    }

    public TrackStatus status() {
        return status;
    }

    public KalmanState filter() {
        return filter;
    }

    public TrackState estimate() {
        return estimate;
    }

    public int detectionCount() {
        return detectionCount;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    /** The time a detection last fed this track — what the miss timeouts are measured from. */
    public Instant lastDetectionAt() {
        return lastDetectionAt;
    }

    private static double seconds(Instant from, Instant to) {
        return Duration.between(from, to).toNanos() / 1_000_000_000.0d;
    }

    /**
     * The result of a transition: the track after it, and the lifecycle event it produced.
     *
     * @param track          the track after the transition
     * @param lifecycleEvent the event, empty when the status did not change
     */
    public record Transition(Track track, Optional<TrackLifecycleEvent> lifecycleEvent) {
    }
}
