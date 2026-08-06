package pk.nust.cuas.threat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pk.nust.cuas.threat.domain.model.ThreatFactor;

/**
 * One row of section 2.4.2's threat-score breakdown.
 *
 * <p>{@code value} and {@code contribution} are null when the factor was unavailable, rather than
 * zero. A JSON zero is indistinguishable from a measured zero, and the whole point of section
 * 6.3.4's confidence requirement is that a client can tell "we measured nothing" from "we measured
 * nothing of concern".
 *
 * @param factor             the factor name
 * @param available          whether it had evidence behind it
 * @param value              the normalised value, or null when unavailable
 * @param weight             the policy weight it carries
 * @param contribution       value x weight, or null when unavailable
 * @param unavailableReason  why it was unavailable, or null when it was not
 */
@Schema(description = "One weighted factor of a composite threat score")
public record ThreatFactorResponse(
        String factor,
        boolean available,
        Double value,
        double weight,
        Double contribution,
        String unavailableReason) {

    /**
     * @param factor the domain factor
     * @return its wire form
     */
    public static ThreatFactorResponse from(ThreatFactor factor) {
        return new ThreatFactorResponse(
                factor.type().name(),
                factor.isAvailable(),
                factor.value().orElse(null),
                factor.weight(),
                factor.isAvailable() ? factor.contribution() : null,
                factor.unavailableReason().orElse(null));
    }
}
