package pk.nust.cuas.config.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Validates a configuration value and rewrites it to a canonical form (ARCHITECTURE.md §4.1.12,
 * "validated config schemas").
 *
 * <p>Canonical means object fields are sorted and formatting is normalised, so the same logical value
 * always stores identically and a before/after diff in the change log reflects a real change rather
 * than a re-ordering. The value must be a JSON object — a namespace configures a set of named
 * settings, never a bare scalar. Deep per-namespace schema validation (a policy's required fields, a
 * sensor's geometry) is a documented follow-on; structural validation is enforced here.
 *
 * <p>Uses its own mapper with deterministic ordering rather than the Spring one, so canonicalisation
 * is a pure function independent of the application's JSON configuration.
 */
@Component
public class ConfigCanonicalizer {

    // Reading into generic Maps and writing with ORDER_MAP_ENTRIES_BY_KEYS sorts keys at every level,
    // which serialising a JsonNode does not — a JsonNode preserves insertion order.
    private final ObjectMapper canonical = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * @param rawJson the submitted value
     * @return the value as canonical JSON, object keys sorted at every level
     * @throws IllegalArgumentException if it is not a well-formed JSON object
     */
    public String canonicalize(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("a configuration value is required");
        }
        Object tree;
        try {
            tree = canonical.readValue(rawJson, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("the configuration value is not valid JSON");
        }
        if (!(tree instanceof Map)) {
            throw new IllegalArgumentException("a configuration value must be a JSON object");
        }
        try {
            return canonical.writeValueAsString(tree);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("the configuration value could not be canonicalised");
        }
    }
}
