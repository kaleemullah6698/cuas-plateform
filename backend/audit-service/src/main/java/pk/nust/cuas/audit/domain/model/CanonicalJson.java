package pk.nust.cuas.audit.domain.model;

import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic JSON serialisation of the audit {@code details} map.
 *
 * <p>The record hash covers {@code canonical_json(details)} (ARCHITECTURE.md section 7.5), so the
 * serialisation must be byte-identical every time the same content is hashed — otherwise a record
 * would fail verification against its own stored digest. Two properties give that guarantee:
 * keys are emitted in lexicographic order, and no insignificant whitespace is produced.
 *
 * <p>Implemented without a JSON library on purpose. The domain stays framework-free, and the
 * canonical form is pinned by this code rather than by a third party's formatting defaults, which
 * could change across versions and silently invalidate every previously stored hash.
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    /**
     * Serialises a string map to canonical JSON.
     *
     * @param values the map to serialise; {@code null} is treated as empty
     * @return canonical JSON, for example {@code {"a":"1","b":"2"}}
     */
    public static String of(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        // TreeMap gives lexicographic key order, which is what makes the output deterministic.
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, entry.getKey());
            out.append(':');
            if (entry.getValue() == null) {
                out.append("null");
            } else {
                writeString(out, entry.getValue());
            }
        }
        return out.append('}').toString();
    }

    /** Writes a JSON string literal, escaping per RFC 8259. */
    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
