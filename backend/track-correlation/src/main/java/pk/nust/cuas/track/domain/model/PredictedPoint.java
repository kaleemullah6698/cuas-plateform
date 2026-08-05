package pk.nust.cuas.track.domain.model;

import java.time.Instant;

/**
 * One point on a track's predicted trajectory: where the filter expects it to be, when, and how
 * confident that is (ARCHITECTURE.md section 4.1.5).
 *
 * <p>{@code uncertaintyM} grows with the horizon, because prediction without measurement only ever
 * loses information. Publishing it is what makes a far-horizon point honest rather than
 * authoritative — an operator, and threat-assessment (section 6.3.4), can then discount a prediction
 * instead of treating a 30-second extrapolation like a fix.
 *
 * @param position     the predicted position
 * @param at           the time it is predicted for
 * @param uncertaintyM the 1σ horizontal position error at that horizon, in metres
 */
public record PredictedPoint(GeoPoint position, Instant at, double uncertaintyM) {

    public PredictedPoint {
        if (position == null || at == null) {
            throw new IllegalArgumentException("position and at are required");
        }
        if (uncertaintyM < 0.0d) {
            throw new IllegalArgumentException("uncertainty cannot be negative, was " + uncertaintyM);
        }
    }
}
