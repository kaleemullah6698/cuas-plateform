package pk.nust.cuas.track.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.TrackMergePolicy;

/**
 * Sweeps for tracks that two sensors have independently opened on one object, and merges the pairs
 * that have held together long enough to be believed (ARCHITECTURE.md section 4.1.5).
 *
 * <p>Each sweep pairs every confirmed track against every other in its sector and asks the policy
 * whether the pair looks like one object <em>now</em>. A pair that does has its evidence advanced by
 * one sweep; a pair that previously did and no longer does has its evidence discarded, because the
 * requirement is <em>consecutive</em> sweeps — a break resets the count rather than pausing it. When
 * a pair's evidence reaches the threshold, the younger track is merged into the older.
 *
 * <p>Each merge commits in its own transaction, like the timeout sweep, so one contended pair cannot
 * roll back the rest of the sweep.
 *
 * <p>A track already merged during this sweep is not considered again. Without that, three
 * mutually-close tracks could produce two merges in one pass, the second of them into a track that
 * had just become terminal — and the sweep's own snapshot would be the only thing that made it look
 * valid.
 */
@Service
public class TrackMergeService {

    private static final Logger log = LoggerFactory.getLogger(TrackMergeService.class);

    private final TrackRepository tracks;
    private final TrackMergePolicy mergePolicy;
    private final TrackMergeApplier applier;
    private final Clock clock;

    public TrackMergeService(TrackRepository tracks, TrackMergePolicy mergePolicy,
                             TrackMergeApplier applier, Clock clock) {
        this.tracks = tracks;
        this.mergePolicy = mergePolicy;
        this.applier = applier;
        this.clock = clock;
    }

    /** Advances every merge candidacy, and merges the pairs that have earned it. */
    public void sweep() {
        Instant now = clock.instant();
        List<Track> confirmed = tracks.findConfirmed();
        if (confirmed.size() < 2) {
            return;
        }
        Map<UUID, Set<String>> sensors = new HashMap<>();
        Set<UUID> mergedThisSweep = new HashSet<>();

        for (int i = 0; i < confirmed.size(); i++) {
            Track a = confirmed.get(i);
            if (mergedThisSweep.contains(a.id())) {
                continue;
            }
            for (int j = i + 1; j < confirmed.size(); j++) {
                Track b = confirmed.get(j);
                if (mergedThisSweep.contains(b.id()) || mergedThisSweep.contains(a.id())) {
                    continue;
                }
                evaluate(a, b, sensors, now, mergedThisSweep);
            }
        }
    }

    private void evaluate(Track a, Track b, Map<UUID, Set<String>> sensors, Instant now,
                          Set<UUID> mergedThisSweep) {
        boolean candidate = mergePolicy.areCandidates(
                a, sensorsOf(a.id(), sensors), b, sensorsOf(b.id(), sensors));
        try {
            if (!candidate) {
                applier.discard(a.id(), b.id());
                return;
            }
            // A merge is not triggered by any detection, so it gets a fresh correlation id for its
            // logs and its published event.
            CorrelationId.set(CorrelationId.generate());
            try {
                if (applier.advanceAndMaybeMerge(a.id(), b.id(), now)) {
                    mergedThisSweep.add(mergePolicy.survivorOf(a, b).id().equals(a.id()) ? b.id() : a.id());
                }
            } finally {
                CorrelationId.clear();
            }
        } catch (DataAccessException ex) {
            // A concurrent detection or timeout changed one of the pair. The sweep is periodic, so
            // the pair is simply re-evaluated on the next tick against its new state.
            log.debug("Merge evaluation of {} and {} failed; retrying next sweep", a.id(), b.id(), ex);
        }
    }

    /** Sensor sets are read once per track per sweep, not once per pair. */
    private Set<String> sensorsOf(UUID trackId, Map<UUID, Set<String>> cache) {
        return cache.computeIfAbsent(trackId, tracks::contributingSensors);
    }
}
