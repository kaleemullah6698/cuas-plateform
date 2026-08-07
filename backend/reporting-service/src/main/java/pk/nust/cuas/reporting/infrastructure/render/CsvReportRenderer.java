package pk.nust.cuas.reporting.infrastructure.render;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.application.port.ReportRenderer;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportDocument.ReportTable;
import pk.nust.cuas.reporting.domain.model.ReportFormat;

/**
 * Renders a report as CSV. Each table is a heading line, a header row, then its rows; fields are
 * quoted per RFC 4180 when they contain a comma, quote or newline, so a value can never break the row
 * structure — the CSV analogue of never interpolating into a query.
 */
@Component
public class CsvReportRenderer implements ReportRenderer {

    @Override
    public ReportFormat format() {
        return ReportFormat.CSV;
    }

    @Override
    public byte[] render(ReportDocument document) {
        StringBuilder csv = new StringBuilder();
        csv.append("# ").append(escape(document.title())).append(System.lineSeparator());
        for (ReportTable table : document.tables()) {
            csv.append(System.lineSeparator()).append("# ").append(escape(table.heading()))
                    .append(System.lineSeparator());
            csv.append(row(table.columns()));
            for (List<String> row : table.rows()) {
                csv.append(row(row));
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String row(List<String> fields) {
        return fields.stream().map(CsvReportRenderer::escape)
                .reduce((a, b) -> a + "," + b).orElse("") + System.lineSeparator();
    }

    private static String escape(String field) {
        String value = field == null ? "" : field;
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
