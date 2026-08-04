package pk.nust.cuas.track.domain.model;

/**
 * The lifecycle state of a track (ARCHITECTURE.md section 4.1.5: tentative → confirmed → coasting →
 * lost).
 *
 * <p>The foundation models only the two states it computes: a track is {@code TENTATIVE} when first
 * created and becomes {@code CONFIRMED} once enough detections have associated to it. {@code
 * COASTING} and {@code LOST} require trajectory prediction and a miss-timeout, which are M5.2 work;
 * they are absent here rather than declared-but-unreachable, so the type only admits states the
 * service can actually be in.
 */
public enum TrackStatus {
    TENTATIVE,
    CONFIRMED
}
