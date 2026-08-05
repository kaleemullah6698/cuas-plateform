package pk.nust.cuas.track.domain.model;

import java.time.Instant;

/**
 * What the AI boundary concluded about a track (ARCHITECTURE.md sections 4.1.5, 6.2).
 *
 * <p>The domain's own shape, mapped from {@code inference.v1.InferenceResponse} at the boundary, so
 * the correlation domain stays free of the inference contract (CLAUDE.md §2). It carries only what
 * a track needs to describe itself; the full inference — every prediction, every attribution — is on
 * {@code ai.inference.completed.v1} for consumers that want it.
 *
 * <p>{@code modelVersion} and {@code device} are kept deliberately. §6.4 requires a mocked result to
 * be distinguishable from a real one wherever it surfaces, and a classification that reached the
 * operator's screen without them would have lost exactly that distinction.
 *
 * @param label        the top prediction, e.g. an airframe class or {@code bird}
 * @param labelClass   the coarse grouping: {@code DRONE}, {@code NON_DRONE}, {@code BACKGROUND}
 * @param confidence   calibrated, in [0,1]
 * @param modelId      which service produced it
 * @param modelVersion its version — {@code 0.0.0-mock} for a mocked result
 * @param device       the compute device, {@code DEVICE_MOCK} for a mocked result
 * @param classifiedAt when the classification was obtained
 */
public record TrackClassification(
        String label,
        String labelClass,
        double confidence,
        String modelId,
        String modelVersion,
        String device,
        Instant classifiedAt) {

    public TrackClassification {
        if (label == null || label.isBlank() || labelClass == null || labelClass.isBlank()) {
            throw new IllegalArgumentException("label and labelClass are required");
        }
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0,1], was " + confidence);
        }
        if (modelId == null || modelVersion == null || device == null || classifiedAt == null) {
            throw new IllegalArgumentException(
                    "modelId, modelVersion, device and classifiedAt are required");
        }
    }

    /**
     * @return whether this result came from the mock provider, which the dashboard renders as an
     *     explicit MOCK INFERENCE badge (§6.4)
     */
    public boolean isMocked() {
        return modelVersion.startsWith("0.0.0-mock") || "DEVICE_MOCK".equals(device);
    }
}
