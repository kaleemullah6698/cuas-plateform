package pk.nust.cuas.orchestrator.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.orchestrator.domain.model.Modality;

class ModalityMapperTest {

    @Test
    void everyDefinedWireModalityMapsToItsDomainCounterpart() {
        assertThat(ModalityMapper.toDomain(SensorModality.MODALITY_RF)).contains(Modality.RF);
        assertThat(ModalityMapper.toDomain(SensorModality.MODALITY_RADAR)).contains(Modality.RADAR);
        assertThat(ModalityMapper.toDomain(SensorModality.MODALITY_EO_IR)).contains(Modality.EO_IR);
        assertThat(ModalityMapper.toDomain(SensorModality.MODALITY_ACOUSTIC)).contains(Modality.ACOUSTIC);
        assertThat(ModalityMapper.toDomain(SensorModality.MODALITY_GNSS)).contains(Modality.GNSS);
        assertThat(ModalityMapper.toDomain(SensorModality.MODALITY_EXTERNAL)).contains(Modality.EXTERNAL);
    }

    @Test
    void anUnspecifiedModalityIsAbsentRatherThanADefault() {
        // Routing an unidentifiable observation "somewhere" would produce a confident-looking result
        // from a model that never saw that kind of input.
        assertThat(ModalityMapper.toDomain(SensorModality.MODALITY_UNSPECIFIED)).isEmpty();
        assertThat(ModalityMapper.toDomain(SensorModality.UNRECOGNIZED)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(SensorModality.class)
    void everyWireValueIsHandledWithoutThrowing(SensorModality modality) {
        // The switch is exhaustive today; this fails the moment the contract gains a modality that
        // nothing has mapped, rather than letting it silently fall through at runtime.
        assertThat(ModalityMapper.toDomain(modality)).isNotNull();
    }

    @Test
    void theDomainEnumCoversEveryDefinedWireModality() {
        long defined = java.util.Arrays.stream(SensorModality.values())
                .filter(m -> m != SensorModality.MODALITY_UNSPECIFIED && m != SensorModality.UNRECOGNIZED)
                .count();

        assertThat(Modality.values()).hasSize((int) defined);
    }
}
