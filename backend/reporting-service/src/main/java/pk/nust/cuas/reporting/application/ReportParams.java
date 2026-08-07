package pk.nust.cuas.reporting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * A typed, validating view over a report job's JSON parameters. Parameters differ per report type
 * (an incident id, a date, a time range), so they travel as JSON and each generator reads what it
 * needs through this — an unusable parameter is an {@link ReportDataException#invalidRange} the job
 * fails on, never an unchecked parse error.
 */
public final class ReportParams {

    private final JsonNode node;

    private ReportParams(JsonNode node) {
        this.node = node;
    }

    public static ReportParams parse(ObjectMapper mapper, String json) {
        try {
            JsonNode parsed = mapper.readTree(json == null || json.isBlank() ? "{}" : json);
            return new ReportParams(parsed);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw ReportDataException.invalidRange("the report parameters are not valid JSON");
        }
    }

    public UUID requireUuid(String key) {
        String text = requireText(key);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw ReportDataException.invalidRange("'" + key + "' is not a valid id");
        }
    }

    public LocalDate requireDate(String key) {
        String text = requireText(key);
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException ex) {
            throw ReportDataException.invalidRange("'" + key + "' is not a valid ISO date");
        }
    }

    /** The [from, to) instant range, from explicit {@code from}/{@code to} or a {@code date}'s whole day. */
    public Range requireRange() {
        Optional<Instant> from = instant("from");
        Optional<Instant> to = instant("to");
        if (from.isPresent() && to.isPresent()) {
            if (!to.get().isAfter(from.get())) {
                throw ReportDataException.invalidRange("'to' must be after 'from'");
            }
            return new Range(from.get(), to.get());
        }
        if (node.hasNonNull("date")) {
            LocalDate day = requireDate("date");
            return new Range(day.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        throw ReportDataException.invalidRange("a 'date' or a 'from'/'to' range is required");
    }

    private Optional<Instant> instant(String key) {
        if (!node.hasNonNull(key)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(node.get(key).asText()));
        } catch (RuntimeException ex) {
            throw ReportDataException.invalidRange("'" + key + "' is not a valid ISO instant");
        }
    }

    private String requireText(String key) {
        if (!node.hasNonNull(key) || node.get(key).asText().isBlank()) {
            throw ReportDataException.invalidRange("'" + key + "' is required");
        }
        return node.get(key).asText();
    }

    /** A half-open instant range. */
    public record Range(Instant from, Instant to) {
    }
}
