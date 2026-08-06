package pk.nust.cuas.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The dependency direction of CLAUDE.md section 2 and ARCHITECTURE.md section 4.3, enforced by the
 * build rather than by review. The same check threat-assessment carries, for the same reason:
 * {@code domain/} must compile without Spring on the classpath, and a domain that has quietly grown
 * a framework dependency is one that can only be exercised by starting an application context.
 */
class LayeringTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/pk/nust/cuas/alert");

    /**
     * {@code org.springframework.http.HttpStatus} is the one documented exception: platform-commons'
     * {@code DomainException} carries the HTTP status a domain error maps to, so every service's
     * domain exceptions import it. Every other Spring package is forbidden, so the exception cannot
     * widen into a general permission.
     */
    private static final List<String> FORBIDDEN_IN_DOMAIN = List.of(
            "org.springframework.context",
            "org.springframework.stereotype",
            "org.springframework.beans",
            "org.springframework.boot",
            "org.springframework.data",
            "org.springframework.transaction",
            "org.springframework.web",
            "org.springframework.kafka",
            "jakarta.persistence",
            "jakarta.validation",
            "com.fasterxml.jackson",
            "org.apache.kafka",
            "io.grpc",
            "com.google.protobuf",
            "io.swagger",
            // The generated contract types. The domain declares its own Severity and Recommendation
            // and the boundary maps them; importing the wire enums here would make domain/ depend on
            // a code generator.
            "pk.nust.cuas.threat.v1",
            "pk.nust.cuas.alert.v1",
            "pk.nust.cuas.track.v1",
            "pk.nust.cuas.ingestion.v1");

    @Test
    void theDomainDependsOnNoFrameworkAndNoGeneratedContract() throws IOException {
        for (Path source : javaSourcesUnder("domain")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN_IN_DOMAIN) {
                assertThat(body)
                        .as("%s imports %s — domain/ must compile without it (CLAUDE.md section 2)",
                                SOURCE_ROOT.relativize(source), forbidden)
                        .doesNotContain("import " + forbidden);
            }
        }
    }

    @Test
    void theDomainDependsOnNoOuterLayer() throws IOException {
        for (Path source : javaSourcesUnder("domain")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(body)
                    .as("%s imports api/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.alert.api");
            assertThat(body)
                    .as("%s imports infrastructure/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.alert.infrastructure");
        }
    }

    @Test
    void theApiNeverReachesIntoInfrastructure() throws IOException {
        for (Path source : javaSourcesUnder("api")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports infrastructure/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.alert.infrastructure");
        }
    }

    @Test
    void infrastructureNeverReachesIntoTheApi() throws IOException {
        for (Path source : javaSourcesUnder("infrastructure")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports api/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.alert.api");
        }
    }

    @Test
    void thisServiceNeverReadsAnotherServicesDatabase() throws IOException {
        // Database-per-service is absolute (CLAUDE.md section 2). An incident holds track ids and
        // assessment ids that belong to other services; a datasource or a repository naming one of
        // their databases would be the first step to reading it.
        //
        // Deliberately blunt rather than comment-aware, and it caught a comment on its first run —
        // which is the same trade-off ModelAgnosticismTest makes in ai-orchestrator, for the same
        // reason: naming another service's database in prose is how the idea of reaching into it
        // gets normalised. Referring to the owning service instead costs nothing and reads better.
        for (Path source : javaSourcesUnder("")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(body)
                    .as("%s names another service's database", SOURCE_ROOT.relativize(source))
                    .doesNotContain("trackdb")
                    .doesNotContain("threatdb")
                    .doesNotContain("notifydb");
        }
    }

    private static List<Path> javaSourcesUnder(String layer) throws IOException {
        Path root = layer.isEmpty() ? SOURCE_ROOT : SOURCE_ROOT.resolve(layer);
        assertThat(root).as("source layer %s should exist", root).exists();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
