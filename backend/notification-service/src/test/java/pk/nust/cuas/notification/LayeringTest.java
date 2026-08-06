package pk.nust.cuas.notification;

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
 * build rather than by review: {@code domain/} must compile without Spring or a generated contract on
 * the classpath, and the outer layers must not cross into each other.
 */
class LayeringTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/pk/nust/cuas/notification");

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
            // The generated contract types. The domain declares its own Channel, Urgency and
            // DeliveryFailureReason and the boundary maps them; importing the wire enums here would
            // make domain/ depend on a code generator.
            "pk.nust.cuas.notification.v1",
            "pk.nust.cuas.threat.v1",
            "pk.nust.cuas.alert.v1",
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
            assertThat(body).as("%s imports api/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.notification.api");
            assertThat(body).as("%s imports infrastructure/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.notification.infrastructure");
            assertThat(body).as("%s imports application/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.notification.application");
        }
    }

    @Test
    void theApiNeverReachesIntoInfrastructure() throws IOException {
        for (Path source : javaSourcesUnder("api")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports infrastructure/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.notification.infrastructure");
        }
    }

    @Test
    void infrastructureNeverReachesIntoTheApi() throws IOException {
        for (Path source : javaSourcesUnder("infrastructure")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports api/", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.notification.api");
        }
    }

    @Test
    void thisServiceNeverReadsAnotherServicesDatabase() throws IOException {
        // Database-per-service is absolute (CLAUDE.md section 2). This service owns notifydb; naming
        // another service's database — even in a comment — is how reaching into it gets normalised.
        for (Path source : javaSourcesUnder("")) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(body).as("%s names another service's database", SOURCE_ROOT.relativize(source))
                    .doesNotContain("alertdb")
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
