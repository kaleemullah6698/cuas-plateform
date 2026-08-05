package pk.nust.cuas.track.domain.model;

/**
 * The lifecycle state of a track (ARCHITECTURE.md section 4.1.5: tentative → confirmed → coasting →
 * lost).
 *
 * <p>A track is {@code TENTATIVE} when created and becomes {@code CONFIRMED} once enough detections
 * have associated to it. A confirmed track that stops being fed detections is predicted forward as
 * {@code COASTING}; if it is still not fed after longer, it becomes {@code LOST} — the terminal
 * state, from which no further transition occurs. A coasting track that a detection re-associates to
 * returns to {@code CONFIRMED}.
 */
public enum TrackStatus {
    TENTATIVE,
    CONFIRMED,
    COASTING,
    LOST;

    /** @return whether a track in this status is still live and can accept detections. */
    public boolean isOpen() {
        return this != LOST;
    }
}
