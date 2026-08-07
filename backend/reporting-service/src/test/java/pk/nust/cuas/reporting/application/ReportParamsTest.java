package pk.nust.cuas.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.reporting.domain.model.ReportFailureReason;

/** Parameter parsing and validation. */
class ReportParamsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aDateExpandsToItsWholeUtcDay() {
        ReportParams.Range range = ReportParams.parse(mapper, "{\"date\":\"2026-08-07\"}").requireRange();

        assertThat(range.from()).isEqualTo(Instant.parse("2026-08-07T00:00:00Z"));
        assertThat(range.to()).isEqualTo(Instant.parse("2026-08-08T00:00:00Z"));
    }

    @Test
    void anExplicitRangeIsUsedAsGiven() {
        ReportParams.Range range = ReportParams.parse(mapper,
                "{\"from\":\"2026-08-07T06:00:00Z\",\"to\":\"2026-08-07T18:00:00Z\"}").requireRange();

        assertThat(range.from()).isEqualTo(Instant.parse("2026-08-07T06:00:00Z"));
        assertThat(range.to()).isEqualTo(Instant.parse("2026-08-07T18:00:00Z"));
    }

    @Test
    void aMissingRangeIsAnInvalidRangeFailure() {
        assertThatThrownBy(() -> ReportParams.parse(mapper, "{}").requireRange())
                .isInstanceOfSatisfying(ReportDataException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(ReportFailureReason.INVALID_RANGE));
    }

    @Test
    void aBackwardsRangeIsRejected() {
        assertThatThrownBy(() -> ReportParams.parse(mapper,
                "{\"from\":\"2026-08-07T18:00:00Z\",\"to\":\"2026-08-07T06:00:00Z\"}").requireRange())
                .isInstanceOf(ReportDataException.class);
    }

    @Test
    void aRequiredIdMustBePresentAndValid() {
        UUID id = UUID.randomUUID();
        assertThat(ReportParams.parse(mapper, "{\"incidentId\":\"" + id + "\"}").requireUuid("incidentId"))
                .isEqualTo(id);
        assertThatThrownBy(() -> ReportParams.parse(mapper, "{}").requireUuid("incidentId"))
                .isInstanceOf(ReportDataException.class);
        assertThatThrownBy(() -> ReportParams.parse(mapper, "{\"incidentId\":\"nope\"}")
                .requireUuid("incidentId")).isInstanceOf(ReportDataException.class);
    }
}
