package pk.nust.cuas.track.application;

import java.time.Clock;
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
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackLifecycleEvent;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.AssociationPolicy;
import pk.nust.cuas.track.domain.service.SectorPolicy;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;

/**
 * Correlates a normalised detection into a track (ARCHITECTURE.md section 4.1.5).
 *
 * <p>The pipeline: place the detection in its sector, gather the open tracks recently seen there,
 * and ask the association policy which one it extends. If a track matches, the detection advances it
 * (and may confirm it); if none does, the detection opens a new tentative track. Either way the
 * aggregate and its history are persisted and the track is published.
 *
 * <p>Persist happens before publish, both inside the transaction: a publish failure rolls the
 * persist back so the redelivery reprocesses the detection rather than leaving a track with no
 * event. Redelivery is otherwise made safe by the {@code detectionAlreadyAssociated} guard — a
 * detection is correlated at most once. The whole method runs single-threaded per instance in the
 * foundation (the consumer is not parallelised), which is what lets the read-modify-write on a track
 * be a plain transaction; concurrent per-sector processing with Redis hot state is M5.2.
 */
@Service
public class DetectionCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(DetectionCorrelationService.class);

    private final TrackRepository tracks;
    private final SectorPolicy sectorPolicy;
    private final AssociationPolicy associationPolicy;
    private final TrackConfirmationPolicy confirmationPolicy;
    private final TrackEventPublisher publisher;
    private final Uuidv7Generator idGenerator;
    private final Clock clock;

    public DetectionCorrelationService(TrackRepository tracks,
                                       SectorPolicy sectorPolicy,
                                       AssociationPolicy associationPolicy,
                                       TrackConfirmationPolicy confirmationPolicy,
                                       TrackEventPublisher publisher,
                                       Uuidv7Generator idGenerator,
                                       Clock clock) {
        this.tracks = tracks;
        this.sectorPolicy = sectorPolicy;
        this.associationPolicy = associationPolicy;
        this.confirmationPolicy = confirmationPolicy;
        this.publisher = publisher;
        this.idGenerator = idGenerator;
        this.clock = clock;
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
            log.debug("Skipping detection {}: no usable position or time to track",
                    record.getDetectionId());
            return;
        }
        IncomingDetection incoming = maybe.get();
        TrackState state = incoming.state();

        if (tracks.detectionAlreadyAssociated(state.detectionId())) {
            log.debug("Detection {} already correlated; ignoring redelivery", state.detectionId());
            return;
        }

        SectorId sector = sectorPolicy.assign(state.position());
        Instant notBefore = state.observedAt().minus(associationPolicy.timeGate());
        List<Track> candidates = tracks.findOpenInSector(sector, notBefore);
        Optional<AssociationPolicy.Match> match =
                associationPolicy.select(state.position(), state.observedAt(), candidates);

        if (match.isPresent()) {
            extend(match.get(), incoming);
        } else {
            open(sector, incoming);
        }
    }

    private void extend(AssociationPolicy.Match match, IncomingDetection incoming) {
        TrackState state = incoming.state();
        Track.AssociationOutcome outcome = match.track().associate(state, confirmationPolicy);
        DetectionAssociation association = DetectionAssociation.onUpdate(
                state.detectionId(), state.sensorId(), incoming.sensorSeq(), match.distanceM(), clock.instant());

        tracks.update(outcome.track(), association, outcome.lifecycleEvent());
        publisher.publishUpdated(outcome.track());
        log.debug("Associated detection {} to track {} at {} m ({} detections, {})",
                state.detectionId(), outcome.track().id(), Math.round(match.distanceM()),
                outcome.track().detectionCount(), outcome.track().status());
    }

    private void open(SectorId sector, IncomingDetection incoming) {
        TrackState state = incoming.state();
        UUID id = idGenerator.generate(state.observedAt());
        Track track = Track.open(id, sector, state);
        DetectionAssociation association = DetectionAssociation.onCreation(
                state.detectionId(), state.sensorId(), incoming.sensorSeq(), clock.instant());

        tracks.create(track, association, TrackLifecycleEvent.creation(TrackStatus.TENTATIVE, state.observedAt()));
        publisher.publishCreated(track);
        log.debug("Opened track {} in sector {} from detection {}",
                id, sector.value(), state.detectionId());
    }
}
