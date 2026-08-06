package pk.nust.cuas.threat;

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
 * build rather than by review.
 *
 * <p>The governing rule is that <strong>{@code domain/} must compile without Spring on the
 * classpath</strong>. That is not a style preference: the scoring rules are the part of this service
 * that has to be readable, testable and defensible in isolation, and a domain that has quietly grown
 * a framework dependency is one that can only be exercised by starting an application context.
 *
 * <p>A source scan rather than bytecode analysis, for the same reason
 * {@code ModelAgnosticismTest} scans source in ai-orchestrator: it is blunt, it has no dependencies
 * of its own, and it catches the import on the line it was added rather than three layers into a
 * transitive graph.
 */
class LayeringTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/pk/nust/cuas/threat");

    /**
     * Frameworks the domain may not reach for. {@code org.springframework.http.HttpStatus} is the
     * one documented exception, and it is not a hole: {@code platform-commons}' {@code
     * DomainException} carries the HTTP status a domain error maps to, so every service's domain
     * exceptions import it. Every other Spring package is forbidden, so the exception cannot widen
     * into a general permission.
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
            "io.swagger");

    @Test
    void theDomainDependsOnNoFramework() throws IOException {
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
                    .as("%s imports api/ — the domain is the centre and depends on nothing outward",
                            SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.threat.api");
            assertThat(body)
                    .as("%s imports infrastructure/ — persistence is an outward adapter",
                            SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.threat.infrastructure");
        }
    }

    @Test
    void theApiNeverReachesIntoInfrastructure() throws IOException {
        for (Path source : javaSourcesUnder("api")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports infrastructure/ — a controller talks to the application layer,"
                            + " never to an adapter", SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.threat.infrastructure");
        }
    }

    @Test
    void infrastructureNeverReachesIntoTheApi() throws IOException {
        for (Path source : javaSourcesUnder("infrastructure")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports api/ — an adapter must not depend on the wire contract",
                            SOURCE_ROOT.relativize(source))
                    .doesNotContain("import pk.nust.cuas.threat.api");
        }
    }

    @Test
    void thereIsNoAiClientInThisService() throws IOException {
        // ARCHITECTURE.md section 4.1.7 and CLAUDE.md section 2: track-correlation -> ai-orchestrator
        // is the pipeline's only synchronous link. The threat-scorer residual reaches this service as
        // an event weighed as one factor of five. A gRPC client here would put an AI service on the
        // critical path and make a degraded model boundary a degraded threat picture.
        for (Path source : javaSourcesUnder("")) {
            assertThat(Files.readString(source, StandardCharsets.UTF_8))
                    .as("%s imports gRPC — threat-assessment must have no synchronous AI dependency",
                            SOURCE_ROOT.relativize(source))
                    .doesNotContain("import io.grpc");
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
