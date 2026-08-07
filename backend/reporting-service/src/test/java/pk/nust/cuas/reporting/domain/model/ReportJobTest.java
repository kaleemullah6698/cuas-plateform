package pk.nust.cuas.reporting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The immutable lifecycle transitions of the {@link ReportJob} aggregate. */
class ReportJobTest {

    private final Instant now = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void aNewJobIsPending() {
        ReportJob job = open();

        assertThat(job.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(job.artifact()).isEmpty();
    }

    @Test
    void succeedingAttachesTheArtifactAndItsRowCount() {
        ArtifactRef artifact = new ArtifactRef("report://x.pdf", "abc", 128L, 42L, ReportFormat.PDF);

        ReportJob done = open().running(now).succeeded(artifact, 55, now);

        assertThat(done.status()).isEqualTo(ReportStatus.SUCCEEDED);
        assertThat(done.artifact()).contains(artifact);
        assertThat(done.rowCount()).isEqualTo(42L);
        assertThat(done.generationMs()).contains(55);
        assertThat(done.completedAt()).contains(now);
    }

    @Test
    void failingRecordsTheReason() {
        ReportJob failed = open().running(now).failed(ReportFailureReason.NO_DATA, "nothing in range", now);

        assertThat(failed.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(failed.failureReason()).contains(ReportFailureReason.NO_DATA);
        assertThat(failed.detail()).contains("nothing in range");
    }

    private ReportJob open() {
        return ReportJob.open(UUID.randomUUID(), ReportType.DAILY_SUMMARY, ReportFormat.PDF,
                "{\"date\":\"2026-08-07\"}", "usr-1", "corr-1", now);
    }
}
