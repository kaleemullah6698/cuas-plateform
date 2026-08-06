package pk.nust.cuas.threat.domain.model;

import java.util.Optional;

/**
 * One weighted input to the composite score — one row of section 2.4.2's breakdown panel.
 *
 * <p><strong>An unavailable factor carries no value at all, not a default one.</strong> Section
 * 6.3.4 requires a missing factor to be reported rather than substituted, and the type enforces it:
 * {@code value} is an {@link Optional} that is empty exactly when the factor could not be computed.
 * A track outside every asset's prediction horizon has no proximity evidence — a 0.0 would read as
 * "very far" and a 1.0 as "on top of the asset", and both are claims the system has no grounds for.
 * The absent factor contributes nothing to the score and shrinks
 * {@link AssessmentConfidence#availableWeight()} instead, so the shortfall is visible rather than
 * hidden inside a plausible-looking number.
 *
 * @param type              which factor this is
 * @param value             the normalised {@code [0,1]} factor value, or empty when unavailable
 * @param weight            the policy weight applied to it
 * @param unavailableReason operator-facing explanation, present exactly when {@code value} is empty
 */
public record ThreatFactor(
        ThreatFactorType type,
        Optional<Double> value,
        double weight,
        Optional<String> unavailableReason) {

    public ThreatFactor {
        if (type == null || value == null || unavailableReason == null) {
            throw new IllegalArgumentException("A threat factor's type, value and reason are required");
        }
        if (weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("Factor weight must be in [0,1], was " + weight);
        }
        value.ifPresent(v -> {
            if (v < 0.0 || v > 1.0 || Double.isNaN(v)) {
                throw new IllegalArgumentException("Factor value must be in [0,1], was " + v);
            }
        });
        if (value.isPresent() == unavailableReason.isPresent()) {
            throw new IllegalArgumentException(
                    "A factor has either a value or a reason it is unavailable, never both or neither");
        }
    }

    /**
     * @param type   which factor
     * @param value  the normalised value, clamped by the caller into {@code [0,1]}
     * @param weight the policy weight
     * @return an available factor
     */
    public static ThreatFactor available(ThreatFactorType type, double value, double weight) {
        return new ThreatFactor(type, Optional.of(value), weight, Optional.empty());
    }

    /**
     * @param type   which factor
     * @param weight the policy weight it would have carried — recorded so the breakdown shows what
     *               the score is missing, not merely that something is missing
     * @param reason operator-facing explanation
     * @return an unavailable factor
     */
    public static ThreatFactor unavailable(ThreatFactorType type, double weight, String reason) {
        return new ThreatFactor(type, Optional.empty(), weight, Optional.of(reason));
    }

    /** @return {@code true} when this factor had evidence behind it */
    public boolean isAvailable() {
        return value.isPresent();
    }

    /**
     * @return {@code value × weight} — the "0.87 × 0.40 = 0.348" column of section 2.4.2, and zero
     *         for an unavailable factor
     */
    public double contribution() {
        return value.orElse(0.0) * weight;
    }
}
