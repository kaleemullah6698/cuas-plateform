package pk.nust.cuas.reporting.infrastructure.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.application.port.ReportRenderer;
import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportFormat;

/** Renders a report as structured JSON — the machine-readable export. */
@Component
public class JsonReportRenderer implements ReportRenderer {

    private final ObjectMapper objectMapper;

    public JsonReportRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ReportFormat format() {
        return ReportFormat.JSON;
    }

    @Override
    public byte[] render(ReportDocument document) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("could not render the report as JSON", ex);
        }
    }
}
