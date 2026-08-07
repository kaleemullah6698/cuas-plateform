package pk.nust.cuas.reporting.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.application.port.ReportRenderer;
import pk.nust.cuas.reporting.domain.model.ReportFormat;

/** Resolves the {@link ReportRenderer} for an export format, indexed by format from every renderer bean. */
@Component
public class ReportRendererRegistry {

    private final Map<ReportFormat, ReportRenderer> renderers = new EnumMap<>(ReportFormat.class);

    public ReportRendererRegistry(List<ReportRenderer> renderers) {
        for (ReportRenderer renderer : renderers) {
            ReportRenderer existing = this.renderers.putIfAbsent(renderer.format(), renderer);
            if (existing != null) {
                throw new IllegalStateException("two renderers for format " + renderer.format());
            }
        }
    }

    public ReportRenderer forFormat(ReportFormat format) {
        ReportRenderer renderer = renderers.get(format);
        if (renderer == null) {
            throw new IllegalStateException("no renderer for format " + format);
        }
        return renderer;
    }
}
