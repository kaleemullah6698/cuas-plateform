package pk.nust.cuas.config;

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
 * domain/} compiles without Spring or a generated contract, and the outer layers do not cross into
 * each other.
 */
class LayeringTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/pk/nust/cuas/config");

    private static final List<String> FORBIDDEN_IN_DOMAIN = List.of(
            "org.springframework.context",
            "org.springframework.stereotype",
            "org.springframework.beans",
            "org.springframework.boot",
            "org.springframework.data",
            "org.springframework.transaction",
            "org.springframework.web",
            "org.springframework.kafka",
            "io.grpc",
            "jakarta.persistence",
            "jakarta.validation",
            "com.fasterxml.jackson",
            "org.apache.kafka",
            "com.google.protobuf",
            "io.swagger",
            "pk.nust.cuas.config.v1");

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
                    .doesNotContain("import pk.nust.cuas.config.api");
            assertThat(body).as("%s imports infrastructure/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.config.infrastructure");
            assertThat(body).as("%s imports application/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.config.application");
        }
    }

    @Test
    void theApiNeverReachesIntoInfrastructurePersistence() throws IOException {
        // The gRPC handler legitimately uses the broadcaster (an infrastructure messaging adapter it is
        // wired to), so the rule that matters for this service is that the API never reaches into the
        // persistence adapters or entities — those belong behind the repository port.
        for (Path source : javaSourcesUnder("api")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports persistence internals", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.config.infrastructure.persistence");
        }
    }

    @Test
    void thisServiceNeverReadsAnotherServicesDatabase() throws IOException {
        for (Path source : javaSourcesUnder("")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(body).as("%s names another service's database", SOURCE_ROOT.relativize(source))
                    .doesNotContain("alertdb")
                    .doesNotContain("notifydb")
                    .doesNotContain("threatdb")
                    .doesNotContain("reportdb");
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
