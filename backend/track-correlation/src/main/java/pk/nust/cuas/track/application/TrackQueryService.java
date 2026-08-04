package pk.nust.cuas.track.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.track.domain.exception.TrackNotFoundException;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.port.TrackRepository;

/**
 * The read side of track correlation, backing the {@code GET /tracks} API (ARCHITECTURE.md section
 * 4.1.5). Thin: it applies the filters and turns an unknown id into a not-found, leaving the query
 * to the repository and the wire shaping to the controller.
 */
@Service
public class TrackQueryService {

    private final TrackRepository tracks;

    public TrackQueryService(TrackRepository tracks) {
        this.tracks = tracks;
    }

    @Transactional(readOnly = true)
    public List<Track> list(Optional<SectorId> sector, Optional<TrackStatus> status, int limit) {
        return tracks.search(sector, status, limit);
    }

    @Transactional(readOnly = true)
    public Track get(UUID id) {
        return tracks.findById(id).orElseThrow(() -> new TrackNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<TrackState> history(UUID id, int limit) {
        if (tracks.findById(id).isEmpty()) {
            throw new TrackNotFoundException(id);
        }
        return tracks.history(id, limit);
    }
}
