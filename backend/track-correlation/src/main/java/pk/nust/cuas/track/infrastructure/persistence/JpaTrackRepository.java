package pk.nust.cuas.track.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import pk.nust.cuas.track.domain.model.DetectionAssociation;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.MergeCandidate;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackLifecycleEvent;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.infrastructure.persistence.entity.MergeCandidateEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.MergeCandidateId;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackDetectionEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackLifecycleEventEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackStateEntity;
import pk.nust.cuas.track.infrastructure.persistence.repository.MergeCandidateJpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackDetectionJpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackJpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackLifecycleEventJpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackStateJpaRepository;

/**
 * JPA implementation of the {@link TrackRepository} port over {@code trackdb}.
 *
 * <p>Each write persists the aggregate row and appends the matching history in the caller's
 * transaction. The Kalman state is serialised to the {@code filter_state} column by
 * {@link KalmanStateCodec}, so a reconstituted track resumes the filter exactly. A new track row is
 * flushed before its history so their foreign keys resolve regardless of Hibernate's insert ordering.
 */
@Repository
public class JpaTrackRepository implements TrackRepository {

    private final TrackJpaRepository tracks;
    private final TrackStateJpaRepository states;
    private final TrackDetectionJpaRepository detections;
    private final TrackLifecycleEventJpaRepository lifecycleEvents;
    private final MergeCandidateJpaRepository mergeCandidates;
    private final Clock clock;

    public JpaTrackRepository(TrackJpaRepository tracks,
                              TrackStateJpaRepository states,
                              TrackDetectionJpaRepository detections,
                              TrackLifecycleEventJpaRepository lifecycleEvents,
                              MergeCandidateJpaRepository mergeCandidates,
                              Clock clock) {
        this.tracks = tracks;
        this.states = states;
        this.detections = detections;
        this.lifecycleEvents = lifecycleEvents;
        this.mergeCandidates = mergeCandidates;
        this.clock = clock;
    }

    @Override
    public List<Track> findOpenInSector(SectorId sector, Instant notBefore) {
        return tracks.findOpenInSector(sector.value(), notBefore).stream()
                .map(JpaTrackRepository::toDomain)
                .toList();
    }

    @Override
    public List<Track> findOpenStaleBefore(Instant threshold) {
        return tracks.findOpenStaleBefore(threshold).stream()
                .map(JpaTrackRepository::toDomain)
                .toList();
    }

    @Override
    public boolean detectionAlreadyAssociated(String detectionId) {
        return detections.existsByDetectionId(detectionId);
    }

    @Override
    public void create(Track track, DetectionAssociation association, TrackLifecycleEvent creation) {
        tracks.saveAndFlush(toEntity(track));
        states.save(toStateEntity(track));
        detections.save(toDetectionEntity(track.id(), association));
        lifecycleEvents.save(toLifecycleEntity(track.id(), creation));
    }

    @Override
    public void update(Track track, DetectionAssociation association,
                       Optional<TrackLifecycleEvent> lifecycleEvent) {
        applyEstimate(track);
        states.save(toStateEntity(track));
        detections.save(toDetectionEntity(track.id(), association));
        lifecycleEvent.ifPresent(event -> lifecycleEvents.save(toLifecycleEntity(track.id(), event)));
    }

    @Override
    public void coast(Track track, TrackLifecycleEvent lifecycleEvent) {
        applyEstimate(track);
        // The predicted estimate is a real point in the track's history, so it is recorded; there is
        // no detection to associate.
        states.save(toStateEntity(track));
        lifecycleEvents.save(toLifecycleEntity(track.id(), lifecycleEvent));
        // No longer CONFIRMED: any accumulated merge evidence is void. A coasting track's position
        // is predicted rather than observed, and merging on a prediction would let two tracks
        // collapse because neither is being measured.
        mergeCandidates.deleteAllInvolving(track.id());
    }

    @Override
    public void lose(Track track, TrackLifecycleEvent lifecycleEvent) {
        TrackEntity entity = load(track.id());
        entity.markLost();
        tracks.save(entity);
        lifecycleEvents.save(toLifecycleEntity(track.id(), lifecycleEvent));
        mergeCandidates.deleteAllInvolving(track.id());
    }

    @Override
    public void merge(Track merged, TrackLifecycleEvent lifecycleEvent) {
        TrackEntity entity = load(merged.id());
        entity.markMerged(merged.mergedInto().orElseThrow(
                () -> new IllegalArgumentException("a merged track must name its survivor: " + merged.id())));
        tracks.save(entity);
        lifecycleEvents.save(toLifecycleEntity(merged.id(), lifecycleEvent));
        // The track is no longer CONFIRMED, so every candidacy it is in is void.
        mergeCandidates.deleteAllInvolving(merged.id());
    }

    @Override
    public List<Track> findConfirmed() {
        return tracks.findConfirmed().stream().map(JpaTrackRepository::toDomain).toList();
    }

    @Override
    public Set<String> contributingSensors(UUID trackId) {
        return new LinkedHashSet<>(detections.findDistinctSensorIds(trackId));
    }

    @Override
    public Optional<MergeCandidate> findMergeCandidate(UUID a, UUID b) {
        return mergeCandidates.findById(MergeCandidateId.of(a, b))
                .map(JpaTrackRepository::toMergeCandidateDomain);
    }

    @Override
    public void saveMergeCandidate(MergeCandidate candidate) {
        mergeCandidates.save(new MergeCandidateEntity(
                new MergeCandidateId(candidate.lowerTrackId(), candidate.higherTrackId()),
                candidate.consecutiveSweeps(), candidate.firstObservedAt(), candidate.lastObservedAt()));
    }

    @Override
    public void deleteMergeCandidate(UUID a, UUID b) {
        mergeCandidates.deleteById(MergeCandidateId.of(a, b));
    }

    @Override
    public void deleteMergeCandidatesFor(UUID trackId) {
        mergeCandidates.deleteAllInvolving(trackId);
    }

    @Override
    public Optional<Track> findById(UUID id) {
        return tracks.findById(id).map(JpaTrackRepository::toDomain);
    }

    @Override
    public List<Track> search(Optional<SectorId> sector, Optional<TrackStatus> status, int limit) {
        Pageable page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "lastDetectionAt"));
        List<TrackEntity> found;
        if (sector.isPresent() && status.isPresent()) {
            found = tracks.findBySectorIdAndStatus(sector.get().value(), status.get().name(), page);
        } else if (sector.isPresent()) {
            found = tracks.findBySectorId(sector.get().value(), page);
        } else if (status.isPresent()) {
            found = tracks.findByStatus(status.get().name(), page);
        } else {
            found = tracks.findAll(page).getContent();
        }
        return found.stream().map(JpaTrackRepository::toDomain).toList();
    }

    @Override
    public List<TrackState> history(UUID trackId, int limit) {
        return states.findByTrackIdOrderByObservedAtDesc(trackId, PageRequest.of(0, limit)).stream()
                .map(JpaTrackRepository::toStateDomain)
                .toList();
    }

    private void applyEstimate(Track track) {
        TrackEntity entity = load(track.id());
        TrackState estimate = track.estimate();
        entity.applyEstimate(track.status().name(),
                estimate.position().latitude(), estimate.position().longitude(), estimate.position().altitudeM(),
                estimate.velocity().speedMs(), estimate.velocity().headingDeg(),
                estimate.detectionId(), estimate.sensorId(), track.detectionCount(),
                estimate.observedAt(), track.lastDetectionAt(), KalmanStateCodec.encode(track.filter()));
        tracks.save(entity);
    }

    private TrackEntity load(UUID id) {
        return tracks.findById(id).orElseThrow(
                () -> new IllegalStateException("track to update no longer exists: " + id));
    }

    private static Track toDomain(TrackEntity entity) {
        KalmanState filter = KalmanStateCodec.decode(entity.filterState());
        TrackState estimate = new TrackState(
                new GeoPoint(entity.latitude(), entity.longitude(), entity.altitudeM()),
                new Velocity(entity.speedMs(), entity.headingDeg()),
                entity.observedAt(), entity.lastDetectionId(), entity.lastSensorId());
        TrackStatus status = TrackStatus.valueOf(entity.status());
        if (status == TrackStatus.MERGED) {
            return Track.reconstituteMerged(entity.trackId(), new SectorId(entity.sectorId()), filter,
                    estimate, entity.detectionCount(), entity.firstSeen(), entity.lastDetectionAt(),
                    entity.mergedIntoTrackId());
        }
        return Track.reconstitute(entity.trackId(), new SectorId(entity.sectorId()), status, filter,
                estimate, entity.detectionCount(), entity.firstSeen(), entity.lastDetectionAt());
    }

    private static MergeCandidate toMergeCandidateDomain(MergeCandidateEntity entity) {
        return new MergeCandidate(entity.getId().getLowerTrackId(), entity.getId().getHigherTrackId(),
                entity.getConsecutiveSweeps(), entity.getFirstObservedAt(), entity.getLastObservedAt());
    }

    private static TrackState toStateDomain(TrackStateEntity entity) {
        return new TrackState(
                new GeoPoint(entity.latitude(), entity.longitude(), entity.altitudeM()),
                new Velocity(entity.speedMs(), entity.headingDeg()),
                entity.observedAt(), entity.detectionId(), entity.sensorId());
    }

    private static TrackEntity toEntity(Track track) {
        TrackState estimate = track.estimate();
        return new TrackEntity(track.id(), track.sector().value(), track.status().name(),
                estimate.position().latitude(), estimate.position().longitude(), estimate.position().altitudeM(),
                estimate.velocity().speedMs(), estimate.velocity().headingDeg(),
                estimate.detectionId(), estimate.sensorId(), track.detectionCount(),
                track.firstSeen(), estimate.observedAt(), track.lastDetectionAt(),
                KalmanStateCodec.encode(track.filter()));
    }

    private TrackStateEntity toStateEntity(Track track) {
        TrackState estimate = track.estimate();
        return new TrackStateEntity(track.id(),
                estimate.position().latitude(), estimate.position().longitude(), estimate.position().altitudeM(),
                estimate.velocity().speedMs(), estimate.velocity().headingDeg(),
                estimate.observedAt(), estimate.detectionId(), estimate.sensorId(), clock.instant());
    }

    private static TrackDetectionEntity toDetectionEntity(UUID trackId, DetectionAssociation association) {
        Double distance = association.distanceM().isPresent()
                ? association.distanceM().getAsDouble() : null;
        return new TrackDetectionEntity(trackId, association.detectionId(), association.sensorId(),
                association.sensorSeq(), distance, association.associatedAt());
    }

    private static TrackLifecycleEventEntity toLifecycleEntity(UUID trackId, TrackLifecycleEvent event) {
        String from = event.from().map(TrackStatus::name).orElse(null);
        return new TrackLifecycleEventEntity(trackId, from, event.to().name(), event.reason(),
                event.occurredAt());
    }
}
