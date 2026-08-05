package pk.nust.cuas.track.domain.service;

import java.util.Collections;
import java.util.Set;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackStatus;

/**
 * Decides whether two tracks are the same object seen by two sensors (ARCHITECTURE.md section 4.1.5,
 * cross-sensor duplicate elimination).
 *
 * <p>Four conditions, all required:
 * <ol>
 *   <li><b>Both {@code CONFIRMED}.</b> A tentative track has not earned the right to absorb or be
 *       absorbed — it may be an artefact that never confirms. A coasting track is excluded too: its
 *       position is predicted rather than observed, and merging on a prediction would let two tracks
 *       collapse because neither is being measured.</li>
 *   <li><b>Same sector.</b> Sector ownership is what makes this decidable by one instance
 *       (section 4.1.5); a cross-sector merge would need coordination this service does not have.</li>
 *   <li><b>Disjoint sensor sets.</b> This is the condition that makes it a <em>cross-sensor</em>
 *       merge. One sensor reporting the same object twice is a duplicate detection, which
 *       association already handles; two tracks sharing a sensor are far more likely to be two real
 *       objects that sensor is resolving separately.</li>
 *   <li><b>Inside the merge gate.</b> Their Kalman estimates are closer together than the gate.</li>
 * </ol>
 *
 * <p>This decides the <em>instantaneous</em> question. The consecutive-sweep requirement — the
 * safeguard that stops two drones in close formation from being collapsed — is a matter of history
 * across sweeps, so it lives with the sweep rather than in this pure function.
 *
 * <p>Pure and framework-free.
 */
public final class TrackMergePolicy {

    private final double mergeGateM;
    private final int requiredConsecutiveSweeps;

    /**
     * @param mergeGateM                how close two estimates must be, in metres
     * @param requiredConsecutiveSweeps how many consecutive sweeps they must stay that close
     * @throws IllegalArgumentException when the gate is not positive or the sweep count is below one
     */
    public TrackMergePolicy(double mergeGateM, int requiredConsecutiveSweeps) {
        if (!(mergeGateM > 0.0d)) {
            throw new IllegalArgumentException("merge gate must be positive, was " + mergeGateM);
        }
        if (requiredConsecutiveSweeps < 1) {
            throw new IllegalArgumentException(
                    "at least one sweep is required, was " + requiredConsecutiveSweeps);
        }
        this.mergeGateM = mergeGateM;
        this.requiredConsecutiveSweeps = requiredConsecutiveSweeps;
    }

    /**
     * Whether these two tracks look like one object on this sweep.
     *
     * @param a        one track
     * @param aSensors the sensors that have fed it
     * @param b        the other track
     * @param bSensors the sensors that have fed it
     * @return whether the pair is a merge candidate right now
     */
    public boolean areCandidates(Track a, Set<String> aSensors, Track b, Set<String> bSensors) {
        if (a.id().equals(b.id())) {
            return false;
        }
        if (a.status() != TrackStatus.CONFIRMED || b.status() != TrackStatus.CONFIRMED) {
            return false;
        }
        if (!a.sector().equals(b.sector())) {
            return false;
        }
        if (!Collections.disjoint(aSensors, bSensors)) {
            return false;
        }
        return a.estimate().position().distanceTo(b.estimate().position()) <= mergeGateM;
    }

    /**
     * @param consecutiveSweeps how many consecutive sweeps a pair has stayed inside the gate
     * @return whether that is enough to merge
     */
    public boolean shouldMerge(int consecutiveSweeps) {
        return consecutiveSweeps >= requiredConsecutiveSweeps;
    }

    /**
     * Which of two merge candidates survives.
     *
     * <p>The older one. Track ids are UUIDv7, which is time-ordered, so "older" is simply the lower
     * id — no timestamp comparison, no tie to break, and every instance would decide identically
     * from the ids alone. The older track is also the one downstream consumers have known longest,
     * so keeping it minimises the re-keying a merge forces on them.
     *
     * @param a one track
     * @param b the other
     * @return the track that survives the merge
     */
    public Track survivorOf(Track a, Track b) {
        return a.id().compareTo(b.id()) <= 0 ? a : b;
    }

    /** @return the number of consecutive sweeps a pair must stay inside the gate */
    public int requiredConsecutiveSweeps() {
        return requiredConsecutiveSweeps;
    }
}
