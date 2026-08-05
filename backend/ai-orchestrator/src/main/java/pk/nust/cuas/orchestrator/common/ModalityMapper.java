package pk.nust.cuas.orchestrator.common;

import java.util.Optional;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.orchestrator.domain.model.Modality;

/**
 * Translates the contract's {@code detection.v1.SensorModality} into the domain's {@link Modality}
 * (ARCHITECTURE.md section 5.3).
 *
 * <p>The anti-corruption layer at the contract boundary. It exists so the domain compiles without the
 * generated types on the classpath (CLAUDE.md section 2), and so a future change to the wire enum —
 * a new modality, a renumbering — lands in one place rather than throughout routing.
 *
 * <p>{@code MODALITY_UNSPECIFIED} and any value this build does not recognise map to <em>absent</em>,
 * not to a default. A detection whose modality cannot be identified must route nowhere rather than
 * route somewhere arbitrary: sending an unknown observation to whichever service happens to be first
 * would produce a confident-looking result from a model that never saw that kind of input.
 */
public final class ModalityMapper {

    private ModalityMapper() {
    }

    /**
     * @param modality the wire modality
     * @return the domain modality, or empty when it is unspecified or unrecognised
     */
    public static Optional<Modality> toDomain(SensorModality modality) {
        return switch (modality) {
            case MODALITY_RF -> Optional.of(Modality.RF);
            case MODALITY_RADAR -> Optional.of(Modality.RADAR);
            case MODALITY_EO_IR -> Optional.of(Modality.EO_IR);
            case MODALITY_ACOUSTIC -> Optional.of(Modality.ACOUSTIC);
            case MODALITY_GNSS -> Optional.of(Modality.GNSS);
            case MODALITY_EXTERNAL -> Optional.of(Modality.EXTERNAL);
            case MODALITY_UNSPECIFIED, UNRECOGNIZED -> Optional.empty();
        };
    }
}
