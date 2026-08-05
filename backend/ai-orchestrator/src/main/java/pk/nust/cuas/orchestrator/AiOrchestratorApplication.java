package pk.nust.cuas.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Track A / Track B boundary: inference routing, circuit breaking, mock fallback
 * (ARCHITECTURE.md sections 4.1.6, 6.1).
 *
 * <p>The only component that knows an AI service exists. It routes by sensor modality to whatever is
 * registered in {@code ai.services}, and reaches each through the {@code InferenceProvider} port —
 * so it never learns what any of them model. Section 6.1's guarantee that a new model is a
 * configuration entry rather than a code change rests entirely on that, and
 * {@code ModelAgnosticismTest} enforces it.
 *
 * <p>Owns no database. Redis holds the inference cache (section 4.1.6).
 */
@SpringBootApplication
public class AiOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOrchestratorApplication.class, args);
    }
}
