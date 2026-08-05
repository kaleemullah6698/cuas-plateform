package pk.nust.cuas.track.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.DetectionAssociation;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackLifecycleEvent;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.AssociationPolicy;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.SectorPolicy;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;
import pk.nust.cuas.track.domain.service.TrackTimeoutPolicy;

/**
 * Correlates a normalised detection into a track (ARCHITECTURE.md section 4.1.5).
 *
 * <p>The pipeline: place the detection in its sector, gather the open tracks recently seen there,
 * predict each forward to the detection's time with its Kalman filter, and ask the association policy
 * which one the detection extends. If a track matches, the detection runs the filter's
 * predict-then-update cycle and advances the track — reviving it if it was coasting; if none matches,
 * a new tentative track is opened with a freshly initialised filter. The published state is always
 * the Kalman estimate, not the raw detection.
 *
 * <p>Persist before publish, both in the transaction, so a publish failure reprocesses the detection.
 * The {@code detectionAlreadyAssociated} guard makes at-least-once redelivery safe, and the consumer
 * is single-threaded, so the read-modify-write on a track is a plain transaction.
 */
@Service
public class DetectionCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(DetectionCorrelationService.class);

    private final TrackRepository tracks;
    private final SectorPolicy sectorPolicy;
    private final AssociationPolicy associationPolicy;
    private final TrackConfirmationPolicy confirmationPolicy;
    private final KalmanFilter kalman;
    private final TrackEventPublisher publisher;
    private final Uuidv7Generator idGenerator;
    private final Clock clock;
    private final Duration candidateWindow;

    public DetectionCorrelationService(TrackRepository tracks,
                                       SectorPolicy sectorPolicy,
                                       AssociationPolicy associationPolicy,
                                       TrackConfirmationPolicy confirmationPolicy,
                                       KalmanFilter kalman,
                                       TrackTimeoutPolicy timeoutPolicy,
                                       TrackEventPublisher publisher,
                                       Uuidv7Generator idGenerator,
                                       Clock clock) {
        this.tracks = tracks;
        this.sectorPolicy = sectorPolicy;
        this.associationPolicy = associationPolicy;
        this.confirmationPolicy = confirmationPolicy;
        this.kalman = kalman;
        this.publisher = publisher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        // A track older than the lost timeout would already be lost, so it is never an association
        // candidate; the window is exactly the lost timeout.
        this.candidateWindow = timeoutPolicy.lostTimeout();
    }

    /**
     * Correlates one normalised detection, creating or updating a track.
     *
     * @param record the detection consumed from {@code sensor.detection.normalized.v1}
     */
    @Transactional
    public void correlate(DetectionRecord record) {
        Optional<IncomingDetection> maybe = DetectionMapper.toIncoming(record);
        if (maybe.isEmpty()) {
            log.debug("Skipping detection {}: no usable position or time to track", record.getDetectionId());
            return;
        }
        TrackState state = maybe.get().state();
        if (tracks.detectionAlreadyAssociated(state.detectionId())) {
            log.debug("Detection {} already correlated; ignoring redelivery", state.detectionId());
            return;
        }

        SectorId sector = sectorPolicy.assign(state.position());
        List<AssociationPolicy.Candidate> candidates = candidates(sector, state.observedAt());
        Optional<AssociationPolicy.Match> match = associationPolicy.select(state.position(), candidates);

        if (match.isPresent()) {
            extend(match.get(), maybe.get());
        } else {
            open(sector, maybe.get());
        }
    }

    private List<AssociationPolicy.Candidate> candidates(SectorId sector, Instant observedAt) {
        Instant notBefore = observedAt.minus(candidateWindow);
        return tracks.findOpenInSector(sector, notBefore).stream()
                .map(track -> {
                    // From the filter's validity time, not the last detection: a coasting track's
                    // filter is already predicted forward, and re-measuring from lastDetectionAt
                    // would advance it twice over the same interval and throw the gate off.
                    double dt = seconds(track.estimate().observedAt(), observedAt);
                    return new AssociationPolicy.Candidate(track, kalman.position(kalman.predict(track.filter(), dt)));
                })
                .toList();
    }

    private void extend(AssociationPolicy.Match match, IncomingDetection incoming) {
        TrackState state = incoming.state();
        Track.Transition transition = match.track().associate(
                state.position(), state.observedAt(), state.detectionId(), state.sensorId(),
                kalman, confirmationPolicy);
        DetectionAssociation association = DetectionAssociation.onUpdate(
                state.detectionId(), state.sensorId(), incoming.sensorSeq(), match.distanceM(), clock.instant());

        tracks.update(transition.track(), association, transition.lifecycleEvent());
        publisher.publishUpdated(transition.track());
        log.debug("Associated detection {} to track {} at {} m ({} detections, {})",
                state.detectionId(), transition.track().id(), Math.round(match.distanceM()),
                transition.track().detectionCount(), transition.track().status());
    }

    private void open(SectorId sector, IncomingDetection incoming) {
        TrackState state = incoming.state();
        KalmanState filter = kalman.initial(state.position(), state.velocity());
        TrackState estimate = new TrackState(kalman.position(filter), kalman.velocity(filter),
                state.observedAt(), state.detectionId(), state.sensorId());
        UUID id = idGenerator.generate(state.observedAt());
        Track track = Track.open(id, sector, filter, estimate);
        DetectionAssociation association = DetectionAssociation.onCreation(
                state.detectionId(), state.sensorId(), incoming.sensorSeq(), clock.instant());

        tracks.create(track, association, TrackLifecycleEvent.creation(TrackStatus.TENTATIVE, state.observedAt()));
        publisher.publishCreated(track);
        log.debug("Opened track {} in sector {} from detection {}", id, sector.value(), state.detectionId());
    }

    private static double seconds(Instant from, Instant to) {
        return Duration.between(from, to).toNanos() / 1_000_000_000.0d;
    }
}
