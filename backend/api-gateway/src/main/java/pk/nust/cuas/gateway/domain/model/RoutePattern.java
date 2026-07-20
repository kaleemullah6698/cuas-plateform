package pk.nust.cuas.gateway.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A method-and-path pattern used to select the policy that applies to a request.
 *
 * <p>Deliberately a small, exact matcher rather than a general-purpose one. Two segment wildcards
 * are supported and no more: {@code *} matches exactly one path segment, {@code **} matches the
 * remaining segments and may appear only as the final segment. A gateway's routing table decides
 * who may reach what, so its matcher should be simple enough to read and to test exhaustively;
 * regular expressions and optional-segment syntaxes are where subtle over-matching hides.
 *
 * <p>Matching is case-sensitive on the path (RFC 9110 leaves path case significance to the origin
 * server, and every route this platform serves is lower-case) and case-insensitive on the method.
 *
 * @param methods the HTTP methods this pattern applies to; empty means any method
 * @param pattern the path pattern, which must be absolute
 */
public record RoutePattern(Set<String> methods, String pattern) {

    private static final String SINGLE = "*";
    private static final String MULTI = "**";

    public RoutePattern {
        Objects.requireNonNull(methods, "methods");
        if (pattern == null || !pattern.startsWith("/")) {
            throw new IllegalArgumentException("pattern must be an absolute path, was " + pattern);
        }
        methods = methods.stream().map(m -> m.toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<String> segments = segmentsOf(pattern);
        for (int i = 0; i < segments.size(); i++) {
            if (MULTI.equals(segments.get(i)) && i != segments.size() - 1) {
                throw new IllegalArgumentException(
                        "'**' may only appear as the final segment, was " + pattern);
            }
        }
    }

    /**
     * Builds a pattern matching the given methods.
     *
     * @param pattern the path pattern
     * @param methods the HTTP methods; none means any
     * @return the pattern
     */
    public static RoutePattern of(String pattern, String... methods) {
        return new RoutePattern(Set.of(methods), pattern);
    }

    /**
     * @param method the request method
     * @param path   the request path, without query string
     * @return whether this pattern applies to the request
     */
    public boolean matches(String method, String path) {
        return matchesMethod(method) && matchesPath(path);
    }

    private boolean matchesMethod(String method) {
        return methods.isEmpty()
                || (method != null && methods.contains(method.toUpperCase(java.util.Locale.ROOT)));
    }

    private boolean matchesPath(String path) {
        if (path == null) {
            return false;
        }
        List<String> expected = segmentsOf(pattern);
        List<String> actual = segmentsOf(path);

        for (int i = 0; i < expected.size(); i++) {
            String segment = expected.get(i);
            if (MULTI.equals(segment)) {
                // A trailing '**' consumes the rest, but must have something to consume.
                return actual.size() > i;
            }
            if (i >= actual.size()) {
                return false;
            }
            if (!SINGLE.equals(segment) && !segment.equals(actual.get(i))) {
                return false;
            }
        }
        return expected.size() == actual.size();
    }

    private static List<String> segmentsOf(String path) {
        return java.util.Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
    }
}
