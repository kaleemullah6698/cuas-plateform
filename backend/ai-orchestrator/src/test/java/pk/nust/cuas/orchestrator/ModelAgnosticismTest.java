package pk.nust.cuas.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The orchestrator must never know a model by name (ARCHITECTURE.md section 6.1).
 *
 * <p>Section 6.1 promises that "adding a fifth model — acoustic, GNSS, or otherwise — is a
 * configuration entry, not a code change anywhere in Track A". That promise is only worth something
 * if it is enforced, and it is the kind of property that erodes one innocuous constant at a time: a
 * special case for the vision service here, a threshold that only applies to RF there, and within a
 * milestone the boundary is fictional.
 *
 * <p>So this scans the orchestrator's own Java source for model and service names. Service ids live
 * in {@code application.yml} as data; if one appears in code, routing has started depending on
 * <em>which</em> model it is talking to, and integrating a trained model stops being a config change.
 *
 * <p>Deliberately a source scan rather than a reflection or bytecode check: the failure this guards
 * against is someone writing the name down, and the string is what needs to be absent.
 */
class ModelAgnosticismTest {

    /**
     * Names the orchestrator must not contain. Service ids from section 6.3 and the modelling terms
     * that would signal a per-model branch. Modality names (RF, RADAR, ACOUSTIC…) are deliberately
     * <em>not</em> here — a modality describes the sensor input and is a legitimate routing key.
     */
    private static final List<String> FORBIDDEN = List.of(
            "rf-classifier", "rfclassifier",
            "vision-detector", "visiondetector",
            "radar-rf-fusion", "radarrffusion",
            "threat-scorer", "threatscorer",
            "acoustic-classifier", "acousticclassifier",
            "gnss-anomaly", "gnssanomaly",
            "yolo", "yamnet", "onnx", "torchscript", "tensorflow", "pytorch", "kaggle");

    @Test
    void noOrchestratorSourceFileNamesAModelOrAService() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                for (String forbidden : FORBIDDEN) {
                    if (content.contains(forbidden)) {
                        violations.add(sourceRoot.relativize(file) + " contains '" + forbidden + "'");
                    }
                }
            }
        }

        assertThat(violations)
                .as("section 6.1: adding a model must be a configuration entry, not a code change. "
                        + "A model or service name in orchestrator source means routing has begun to "
                        + "depend on which model it is, and integration is no longer config-only.")
                .isEmpty();
    }

    @Test
    void theDefaultProfileRegistersNoServices() {
        // A default profile that shipped example services would register them in every deployment,
        // including production — four mock-backed services nobody asked for, which is the
        // config-level version of mistaking a mock for a real model. The registry is a deployment's
        // own statement of what it runs.
        assertThat(readConfig("application.yml"))
                .contains("services: {}")
                .doesNotContain("mode: mock");
    }

    @Test
    void theRegistryIsConfiguredByDataNotByCode() {
        // The corollary: the service ids do live somewhere, and that somewhere is configuration —
        // here the dev profile's worked example, which is the same shape a production registry takes.
        assertThat(readConfig("application-dev.yml"))
                .contains("ai:").contains("services:").contains("modalities:").contains("mode: mock");
    }

    private static String readConfig(String name) {
        try {
            return Files.readString(Path.of("src/main/resources", name), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("could not read " + name, ex);
        }
    }
}
