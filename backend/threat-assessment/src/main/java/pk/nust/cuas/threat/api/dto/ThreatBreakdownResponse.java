package pk.nust.cuas.threat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;

/**
 * The explainability surface of section 2.4.2: the assessment plus every factor behind it.
 *
 * <p>Separate from {@link ThreatAssessmentResponse} because the two answer different questions and
 * are read at different rates — a track list renders the score, and only a selected track renders
 * the breakdown.
 *
 * @param assessment the assessment
 * @param factors    every factor the policy defined, available or not
 * @param reportingModels the services that have reported on this track, for the evidence panel
 */
@Schema(description = "A threat assessment with its full per-factor breakdown")
public record ThreatBreakdownResponse(
        ThreatAssessmentResponse assessment,
        List<ThreatFactorResponse> factors,
        List<String> reportingModels) {

    /**
     * @param assessment the domain assessment
     * @return its wire form
     */
    public static ThreatBreakdownResponse from(ThreatAssessment assessment) {
        return new ThreatBreakdownResponse(
                ThreatAssessmentResponse.from(assessment),
                assessment.factors().stream().map(ThreatFactorResponse::from).toList(),
                assessment.evidence().reportingModels());
    }
}
