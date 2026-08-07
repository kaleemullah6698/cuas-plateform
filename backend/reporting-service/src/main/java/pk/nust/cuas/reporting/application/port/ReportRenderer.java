package pk.nust.cuas.reporting.application.port;

import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportFormat;

/**
 * Renders a {@link ReportDocument} into an export format's bytes (ARCHITECTURE.md §4.1.10). One
 * renderer per {@link ReportFormat}; renderers and generators compose, so adding a format does not
 * touch a generator and adding a report does not touch a renderer.
 */
public interface ReportRenderer {

    ReportFormat format();

    byte[] render(ReportDocument document);
}
