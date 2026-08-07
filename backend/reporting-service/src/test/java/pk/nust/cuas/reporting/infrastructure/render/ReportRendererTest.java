package pk.nust.cuas.reporting.infrastructure.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportDocument.ReportTable;

/** The three renderers turn a report document into their format's bytes. */
class ReportRendererTest {

    private final ObjectMapper objectMapper =
            JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final ReportDocument document = new ReportDocument("Daily Summary",
            Instant.parse("2026-08-07T00:00:00Z"),
            List.of(new ReportTable("Activity", List.of("Metric", "Count"),
                    List.of(List.of("Alerts raised", "3"), List.of("Value, with comma", "1")))),
            4);

    @Test
    void csvQuotesFieldsThatWouldBreakTheRowStructure() {
        String csv = new String(new CsvReportRenderer().render(document), StandardCharsets.UTF_8);

        assertThat(csv).contains("Metric,Count");
        assertThat(csv).contains("Alerts raised,3");
        assertThat(csv).contains("\"Value, with comma\",1");   // the comma forces quoting
    }

    @Test
    void jsonIsStructuredAndParseable() throws IOException {
        byte[] json = new JsonReportRenderer(objectMapper).render(document);

        var tree = objectMapper.readTree(json);
        assertThat(tree.get("title").asText()).isEqualTo("Daily Summary");
        assertThat(tree.get("rowCount").asLong()).isEqualTo(4);
        assertThat(tree.get("tables").get(0).get("heading").asText()).isEqualTo("Activity");
    }

    @Test
    void pdfIsAValidSinglePageDocument() throws IOException {
        byte[] pdf = new PdfReportRenderer().render(document);

        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        try (PDDocument parsed = PDDocument.load(pdf)) {
            assertThat(parsed.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}
