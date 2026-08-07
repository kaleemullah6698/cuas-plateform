package pk.nust.cuas.reporting.infrastructure.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.application.port.ReportRenderer;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportDocument.ReportTable;
import pk.nust.cuas.reporting.domain.model.ReportFormat;

/**
 * Renders a report as a PDF — the human-readable export an operator files or shares (ARCHITECTURE.md
 * §4.1.10). A simple flowed layout: the title, then each table as a heading, a header row and its
 * rows, paginating when the page fills. PDFBox writes the document to a byte array; nothing touches
 * the filesystem here — storing the bytes is the artifact store's job.
 */
@Component
public class PdfReportRenderer implements ReportRenderer {

    private static final PDFont BODY = PDType1Font.HELVETICA;
    private static final PDFont HEADING = PDType1Font.HELVETICA_BOLD;
    private static final float MARGIN = 50f;
    private static final float LEADING = 15f;
    private static final float BODY_SIZE = 9f;
    private static final float HEADING_SIZE = 12f;
    private static final float TITLE_SIZE = 16f;

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] render(ReportDocument document) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Cursor cursor = new Cursor(pdf);
            cursor.line(document.title(), HEADING, TITLE_SIZE);
            cursor.line("Generated " + document.generatedAt() + "  ·  "
                    + document.rowCount() + " source records", BODY, BODY_SIZE);
            cursor.blank();
            for (ReportTable table : document.tables()) {
                cursor.line(table.heading(), HEADING, HEADING_SIZE);
                cursor.line(String.join("    ", table.columns()), BODY, BODY_SIZE);
                for (List<String> row : table.rows()) {
                    cursor.line(String.join("    ", row), BODY, BODY_SIZE);
                }
                cursor.blank();
            }
            cursor.close();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("could not render the report as PDF", ex);
        }
    }

    /** A flowing text cursor that starts a new page when the current one fills. */
    private static final class Cursor {

        private final PDDocument pdf;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument pdf) throws IOException {
            this.pdf = pdf;
            newPage();
        }

        void line(String text, PDFont font, float size) throws IOException {
            if (y <= MARGIN) {
                newPage();
            }
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(sanitize(text));
            stream.endText();
            y -= LEADING;
        }

        void blank() {
            y -= LEADING;
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        void close() throws IOException {
            stream.close();
        }

        // WinAnsi (PDType1Font Helvetica) cannot encode every character a projected attribute might
        // carry; replace anything outside the printable Latin-1 range so rendering never throws.
        private static String sanitize(String text) {
            StringBuilder safe = new StringBuilder(text.length());
            for (char c : text.toCharArray()) {
                safe.append(c >= 32 && c < 127 || c >= 160 && c <= 255 ? c : '?');
            }
            return safe.toString();
        }
    }
}
