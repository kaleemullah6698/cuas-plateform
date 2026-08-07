package pk.nust.cuas.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.reporting.application.port.ReportGenerator;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportType;

/** The type-to-generator index built at startup. */
class ReportGeneratorRegistryTest {

    @Test
    void itResolvesTheGeneratorForAType() {
        ReportGenerator incident = stub(ReportType.INCIDENT);
        ReportGeneratorRegistry registry =
                new ReportGeneratorRegistry(List.of(incident, stub(ReportType.DAILY_SUMMARY)));

        assertThat(registry.forType(ReportType.INCIDENT)).isSameAs(incident);
    }

    @Test
    void twoGeneratorsForOneTypeFailAtStartup() {
        assertThatThrownBy(() -> new ReportGeneratorRegistry(
                List.of(stub(ReportType.INCIDENT), stub(ReportType.INCIDENT))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INCIDENT");
    }

    @Test
    void aMissingGeneratorFailsRatherThanDroppingTheJob() {
        ReportGeneratorRegistry registry = new ReportGeneratorRegistry(List.of(stub(ReportType.INCIDENT)));

        assertThatThrownBy(() -> registry.forType(ReportType.FUSION_EFFICACY))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ReportGenerator stub(ReportType type) {
        return new ReportGenerator() {
            @Override
            public ReportType type() {
                return type;
            }

            @Override
            public ReportDocument generate(ReportJob job) {
                return null;
            }
        };
    }
}
