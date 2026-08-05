package pk.nust.cuas.track.domain.model;

/**
 * The lifecycle state of a track (ARCHITECTURE.md section 4.1.5: tentative → confirmed → coasting →
 * lost).
 *
 * <p>A track is {@code TENTATIVE} when created and becomes {@code CONFIRMED} once enough detections
 * have associated to it. A confirmed track that stops being fed detections is predicted forward as
 * {@code COASTING}; if it is still not fed after longer, it becomes {@code LOST} — a terminal state,
 * from which no further transition occurs. A coasting track that a detection re-associates to
 * returns to {@code CONFIRMED}.
 *
 * <p>{@code MERGED} is the other terminal state: two sensors independently opened a track on one
 * object, and this one was absorbed into the survivor (section 4.1.5, cross-sensor duplicate
 * elimination). It is distinct from {@code LOST} because the two mean opposite things downstream —
 * a lost track is an object the system stopped seeing, a merged one is an object it is still
 * tracking under a different id. Collapsing them would tell an operator a live threat had gone away.
 */
public enum TrackStatus {
    TENTATIVE,
    CONFIRMED,
    COASTING,
    LOST,
    MERGED;

    /** @return whether a track in this status is still live and can accept detections. */
    public boolean isOpen() {
        return this != LOST && this != MERGED;
    }

    /** @return whether this status is terminal — no transition leads out of it. */
    public boolean isTerminal() {
        return !isOpen();
    }
}
