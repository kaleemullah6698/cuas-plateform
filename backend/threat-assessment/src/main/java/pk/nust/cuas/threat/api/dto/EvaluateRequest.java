package pk.nust.cuas.threat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;

/**
 * Hypothetical evidence for the {@code POST /threats/evaluate} what-if of section 4.1.7.
 *
 * <p>Describes a situation rather than naming a track, so a policy can be tried against a scenario
 * that has not happened. Nothing is persisted and nothing is published — a what-if that raised a
 * real alert would be worse than having no what-if at all.
 *
 * @param policyVersion           the version to evaluate against, or null for the one in force
 * @param sectorId                the sector, for the returned assessment
 * @param speedMs                 the hypothetical track's ground speed
 * @param approach                the hypothetical CPA, or null for a track with no asset nearby
 * @param consecutiveClosingCount how many consecutive updates the track has been closing
 * @param observations            the hypothetical AI verdicts
 */
@Schema(description = "Hypothetical evidence to score against a policy, without persisting anything")
public record EvaluateRequest(
        @Size(max = 64) String policyVersion,
        @Size(max = 128) String sectorId,
        @NotNull @PositiveOrZero Double speedMs,
        @Valid ApproachRequest approach,
        @Min(0) int consecutiveClosingCount,
        List<@Valid ObservationRequest> observations) {

    /**
     * @param at the instant to date the hypothetical observation
     * @return the evidence to score
     */
    public TrackEvidence toEvidence(Instant at) {
        TrackSnapshot snapshot = new TrackSnapshot(
                // A fixed nil id: the scenario is not a track, and minting a random id per request
                // would put a different value in every what-if response for no purpose.
                new UUID(0L, 0L),
                sectorId == null ? "" : sectorId,
                at,
                speedMs,
                Optional.ofNullable(approach).map(ApproachRequest::toApproach),
                "");
        Map<String, InferenceObservation> byModel = new LinkedHashMap<>();
        if (observations != null) {
            observations.forEach(o -> byModel.put(o.modelId(), o.toObservation(at)));
        }
        return new TrackEvidence(Optional.of(snapshot), byModel, consecutiveClosingCount);
    }

    /**
     * @param assetId    the protected asset
     * @param assetName  what the operator would see
     * @param distanceM  separation at the closest point
     * @param timeToCpaS seconds until that point
     * @param closing    false for a receding track
     */
    @Schema(description = "A hypothetical closest point of approach")
    public record ApproachRequest(
            @NotBlank @Size(max = 128) String assetId,
            @Size(max = 256) String assetName,
            @NotNull @PositiveOrZero Double distanceM,
            @NotNull @PositiveOrZero Double timeToCpaS,
            boolean closing) {

        AssetApproach toApproach() {
            return new AssetApproach(assetId, assetName == null ? "" : assetName,
                    distanceM, timeToCpaS, closing);
        }
    }

    /**
     * @param modelId      the hypothetical reporting service
     * @param modelVersion its version
     * @param label        the predicted label
     * @param labelClass   the prediction's class
     * @param confidence   calibrated confidence in [0,1]
     */
    @Schema(description = "A hypothetical AI service verdict")
    public record ObservationRequest(
            @NotBlank @Size(max = 128) String modelId,
            @Size(max = 64) String modelVersion,
            @Size(max = 128) String label,
            @Size(max = 64) String labelClass,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence) {

        InferenceObservation toObservation(Instant at) {
            return new InferenceObservation(modelId, modelVersion, label, labelClass, confidence, at);
        }
    }
}
