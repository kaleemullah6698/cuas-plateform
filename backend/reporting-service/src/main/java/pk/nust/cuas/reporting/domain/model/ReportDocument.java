package pk.nust.cuas.reporting.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * The structured content of a generated report, before it is rendered to a format (ARCHITECTURE.md
 * §4.1.10). A generator produces this; a renderer turns it into PDF, JSON or CSV — so the four
 * generators and the three formats compose without a generator knowing any format.
 *
 * @param title       the report title
 * @param generatedAt when it was produced
 * @param tables      the sections, each a titled table
 * @param rowCount    the number of source records summarised, carried onto the artifact and the event
 */
public record ReportDocument(String title, Instant generatedAt, List<ReportTable> tables, long rowCount) {

    public ReportDocument {
        if (title == null || generatedAt == null || tables == null) {
            throw new IllegalArgumentException("title, generatedAt and tables are required");
        }
        tables = List.copyOf(tables);
    }

    /**
     * One titled table in a report.
     *
     * @param heading the section heading
     * @param columns the column headers
     * @param rows    the rows, each the same length as {@code columns}
     */
    public record ReportTable(String heading, List<String> columns, List<List<String>> rows) {

        public ReportTable {
            if (heading == null || columns == null || rows == null) {
                throw new IllegalArgumentException("heading, columns and rows are required");
            }
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
        }
    }
}
