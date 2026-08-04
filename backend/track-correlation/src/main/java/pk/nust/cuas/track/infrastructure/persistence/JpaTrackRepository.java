package pk.nust.cuas.track.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import pk.nust.cuas.track.domain.model.DetectionAssociation;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackLifecycleEvent;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackDetectionEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackLifecycleEventEntity;
import pk.nust.cuas.track.infrastructure.persistence.entity.TrackStateEntity;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackDetectionJpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackJpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackLifecycleEventJpaRepository;
import pk.nust.cuas.track.infrastructure.persistence.repository.TrackStateJpaRepository;

/**
 * JPA implementation of the {@link TrackRepository} port over {@code trackdb}.
 *
 * <p>{@link #create} and {@link #update} write the aggregate row and append its history in one
 * transaction (the caller's). A new track row is flushed before its history rows so their foreign
 * keys resolve regardless of Hibernate's insert ordering. The domain and the entities are separate
 * types, mapped explicitly here.
 */
@Repository
public class JpaTrackRepository implements TrackRepository {

    private final TrackJpaRepository tracks;
    private final TrackStateJpaRepository states;
    private final TrackDetectionJpaRepository detections;
    private final TrackLifecycleEventJpaRepository lifecycleEvents;
    private final Clock clock;

    public JpaTrackRepository(TrackJpaRepository tracks,
                              TrackStateJpaRepository states,
                              TrackDetectionJpaRepository detections,
                              TrackLifecycleEventJpaRepository lifecycleEvents,
                              Clock clock) {
        this.tracks = tracks;
        this.states = states;
        this.detections = detections;
        this.lifecycleEvents = lifecycleEvents;
        this.clock = clock;
    }

    @Override
    public List<Track> findOpenInSector(SectorId sector, Instant notBefore) {
        return tracks.findBySectorIdAndLastSeenGreaterThanEqual(sector.value(), notBefore).stream()
                .map(JpaTrackRepository::toDomain)
                .toList();
    }

    @Override
    public boolean detectionAlreadyAssociated(String detectionId) {
        return detections.existsByDetectionId(detectionId);
    }

    @Override
    public void create(Track track, DetectionAssociation association, TrackLifecycleEvent creation) {
        // Flush the parent before the history rows so their foreign keys resolve.
        tracks.saveAndFlush(toEntity(track));
        states.save(toStateEntity(track));
        detections.save(toDetectionEntity(track.id(), association));
        lifecycleEvents.save(toLifecycleEntity(track.id(), creation));
    }

    @Override
    public void update(Track track, DetectionAssociation association,
                       Optional<TrackLifecycleEvent> lifecycleEvent) {
        TrackEntity entity = tracks.findById(track.id()).orElseThrow(
                () -> new IllegalStateException("track to update no longer exists: " + track.id()));
        TrackState state = track.state();
        entity.applyState(track.status().name(),
                state.position().latitude(), state.position().longitude(), state.position().altitudeM(),
                state.velocity().speedMs(), state.velocity().headingDeg(),
                state.detectionId(), state.sensorId(), track.detectionCount(), state.observedAt());
        tracks.save(entity);
        states.save(toStateEntity(track));
        detections.save(toDetectionEntity(track.id(), association));
        lifecycleEvent.ifPresent(event -> lifecycleEvents.save(toLifecycleEntity(track.id(), event)));
    }

    @Override
    public Optional<Track> findById(UUID id) {
        return tracks.findById(id).map(JpaTrackRepository::toDomain);
    }

    @Override
    public List<Track> search(Optional<SectorId> sector, Optional<TrackStatus> status, int limit) {
        Pageable page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "lastSeen"));
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

    private static Track toDomain(TrackEntity entity) {
        TrackState state = new TrackState(
                new GeoPoint(entity.latitude(), entity.longitude(), entity.altitudeM()),
                new Velocity(entity.speedMs(), entity.headingDeg()),
                entity.lastSeen(), entity.lastDetectionId(), entity.lastSensorId());
        return Track.reconstitute(entity.trackId(), new SectorId(entity.sectorId()),
                TrackStatus.valueOf(entity.status()), state, entity.detectionCount(), entity.firstSeen());
    }

    private static TrackState toStateDomain(TrackStateEntity entity) {
        return new TrackState(
                new GeoPoint(entity.latitude(), entity.longitude(), entity.altitudeM()),
                new Velocity(entity.speedMs(), entity.headingDeg()),
                entity.observedAt(), entity.detectionId(), entity.sensorId());
    }

    private static TrackEntity toEntity(Track track) {
        TrackState state = track.state();
        return new TrackEntity(track.id(), track.sector().value(), track.status().name(),
                state.position().latitude(), state.position().longitude(), state.position().altitudeM(),
                state.velocity().speedMs(), state.velocity().headingDeg(),
                state.detectionId(), state.sensorId(), track.detectionCount(),
                track.firstSeen(), state.observedAt());
    }

    private TrackStateEntity toStateEntity(Track track) {
        TrackState state = track.state();
        return new TrackStateEntity(track.id(),
                state.position().latitude(), state.position().longitude(), state.position().altitudeM(),
                state.velocity().speedMs(), state.velocity().headingDeg(),
                state.observedAt(), state.detectionId(), state.sensorId(), clock.instant());
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
