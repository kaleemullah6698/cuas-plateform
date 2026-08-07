package pk.nust.cuas.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The dependency direction of CLAUDE.md §2 and ARCHITECTURE.md §4.3, enforced by the build: {@code
 * domain/} compiles without Spring, a JSON library, a renderer library or a generated contract, and
 * the outer layers do not cross into each other.
 */
class LayeringTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/pk/nust/cuas/reporting");

    private static final List<String> FORBIDDEN_IN_DOMAIN = List.of(
            "org.springframework",
            "jakarta.persistence",
            "jakarta.validation",
            "com.fasterxml.jackson",
            "org.apache.kafka",
            "org.apache.pdfbox",
            "com.google.protobuf",
            "io.swagger",
            "pk.nust.cuas.report.v1",
            "pk.nust.cuas.alert.v1",
            "pk.nust.cuas.threat.v1",
            "pk.nust.cuas.track.v1",
            "pk.nust.cuas.ingestion.v1",
            "pk.nust.cuas.inference.v1");

    @Test
    void theDomainDependsOnNoFrameworkAndNoGeneratedContract() throws IOException {
        for (Path source : javaSourcesUnder("domain")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN_IN_DOMAIN) {
                assertThat(body)
                        .as("%s imports %s — domain/ must compile without it (CLAUDE.md §2)",
                                SOURCE_ROOT.relativize(source), forbidden)
                        .doesNotContain("import " + forbidden);
            }
        }
    }

    @Test
    void theDomainDependsOnNoOuterLayer() throws IOException {
        for (Path source : javaSourcesUnder("domain")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(body).as("%s imports api/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.reporting.api");
            assertThat(body).as("%s imports infrastructure/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.reporting.infrastructure");
            assertThat(body).as("%s imports application/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.reporting.application");
        }
    }

    @Test
    void theApiNeverReachesIntoInfrastructure() throws IOException {
        for (Path source : javaSourcesUnder("api")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports infrastructure/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.reporting.infrastructure");
        }
    }

    @Test
    void thisServiceNeverReadsAnotherServicesDatabase() throws IOException {
        for (Path source : javaSourcesUnder("")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(body).as("%s names another service's database", SOURCE_ROOT.relativize(source))
                    .doesNotContain("alertdb")
                    .doesNotContain("notifydb")
                    .doesNotContain("configdb")
                    .doesNotContain("threatdb")
                    .doesNotContain("trackdb");
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
