package pk.nust.cuas.gateway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The matcher decides who may reach what, so its edges are pinned rather than assumed.
 */
class RoutePatternTest {

    @Test
    void literalPathMatchesOnlyItself() {
        RoutePattern pattern = RoutePattern.of("/api/v1/users", "GET");

        assertThat(pattern.matches("GET", "/api/v1/users")).isTrue();
        assertThat(pattern.matches("GET", "/api/v1/user")).isFalse();
        assertThat(pattern.matches("GET", "/api/v1/users/42")).isFalse();
    }

    @Test
    void singleWildcardMatchesExactlyOneSegment() {
        RoutePattern pattern = RoutePattern.of("/api/v1/users/*", "GET");

        assertThat(pattern.matches("GET", "/api/v1/users/42")).isTrue();
        // One segment, not several: otherwise a rule for a user would also cover their roles.
        assertThat(pattern.matches("GET", "/api/v1/users/42/roles")).isFalse();
        assertThat(pattern.matches("GET", "/api/v1/users")).isFalse();
    }

    @Test
    void trailingWildcardMatchesTheRemainderButNotTheBareCollection() {
        RoutePattern pattern = RoutePattern.of("/api/v1/audit/**");

        assertThat(pattern.matches("GET", "/api/v1/audit/verify")).isTrue();
        assertThat(pattern.matches("GET", "/api/v1/audit/a/b/c")).isTrue();
        assertThat(pattern.matches("GET", "/api/v1/audit")).isFalse();
    }

    @Test
    void methodIsMatchedCaseInsensitivelyAndAnEmptySetMatchesAny() {
        assertThat(RoutePattern.of("/x", "post").matches("POST", "/x")).isTrue();
        assertThat(RoutePattern.of("/x", "POST").matches("GET", "/x")).isFalse();
        assertThat(RoutePattern.of("/x").matches("DELETE", "/x")).isTrue();
    }

    @Test
    void repeatedAndTrailingSlashesDoNotDefeatAPattern() {
        RoutePattern pattern = RoutePattern.of("/api/v1/users", "POST");

        // A caller who adds a slash must not slip past a rule that would otherwise apply.
        assertThat(pattern.matches("POST", "/api/v1/users/")).isTrue();
        assertThat(pattern.matches("POST", "//api//v1//users")).isTrue();
    }

    @Test
    void multiWildcardIsRejectedAnywhereButTheEnd() {
        assertThatThrownBy(() -> RoutePattern.of("/api/**/users"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("final segment");
    }

    @Test
    void relativePatternIsRejected() {
        assertThatThrownBy(() -> RoutePattern.of("api/v1/users"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
