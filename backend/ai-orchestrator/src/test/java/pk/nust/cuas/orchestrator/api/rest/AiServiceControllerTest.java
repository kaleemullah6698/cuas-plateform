package pk.nust.cuas.orchestrator.api.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The admin API of §4.1.6, guarded by {@code ai:configure} (§7.4).
 *
 * <p>No Kafka or Redis: the registry and its versions are in memory, so this exercises the HTTP
 * surface, its validation and its authorisation without containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ai.services.some-service.modalities=RF,RADAR",
        "ai.services.some-service.mode=mock",
        "ai.services.some-service.timeout=PT0.2S",
        "ai.services.some-service.max-concurrent=8",
        "spring.kafka.bootstrap-servers=localhost:59998"})
class AiServiceControllerTest {

    private static final SimpleGrantedAuthority AI_CONFIGURE = new SimpleGrantedAuthority("ai:configure");
    private static final SimpleGrantedAuthority TRACK_READ = new SimpleGrantedAuthority("track:read");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theRegistryIsListedWithItsLiveHealth() throws Exception {
        mockMvc.perform(get("/api/v1/ai/services").with(jwt().authorities(AI_CONFIGURE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("some-service"))
                .andExpect(jsonPath("$[0].mode").value("MOCK"))
                .andExpect(jsonPath("$[0].modalities", org.hamcrest.Matchers.containsInAnyOrder("RF", "RADAR")))
                .andExpect(jsonPath("$[0].timeoutMs").value(200))
                .andExpect(jsonPath("$[0].maxConcurrent").value(8))
                // The mock is always healthy — a fallback that could itself be unavailable is none.
                .andExpect(jsonPath("$[0].healthy").value(true));
    }

    @Test
    void listingRequiresAiConfigure() throws Exception {
        // Guarded as well as the write: the registry discloses every AI service's endpoint and
        // health, which is a map of the deployment's internal topology.
        mockMvc.perform(get("/api/v1/ai/services")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ai/services").with(jwt().authorities(TRACK_READ)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aModelVersionCanBeSetAndTakesEffectImmediately() throws Exception {
        mockMvc.perform(post("/api/v1/ai/services/{id}/model-version", "some-service")
                        .with(jwt().authorities(AI_CONFIGURE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelVersion":"1.3.0","canaryVersion":"","canaryPercentage":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelVersion").value("1.3.0"));

        mockMvc.perform(get("/api/v1/ai/services").with(jwt().authorities(AI_CONFIGURE)))
                .andExpect(jsonPath("$[0].modelVersion").value("1.3.0"));
    }

    @Test
    void aCanaryShareCanBeIntroduced() throws Exception {
        mockMvc.perform(post("/api/v1/ai/services/{id}/model-version", "some-service")
                        .with(jwt().authorities(AI_CONFIGURE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelVersion":"1.2.0","canaryVersion":"1.3.0","canaryPercentage":10}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canaryVersion").value("1.3.0"))
                .andExpect(jsonPath("$.canaryPercentage").value(10));
    }

    @Test
    void settingAVersionRequiresAiConfigure() throws Exception {
        String body = """
                {"modelVersion":"1.3.0","canaryVersion":"","canaryPercentage":0}""";

        mockMvc.perform(post("/api/v1/ai/services/{id}/model-version", "some-service")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ai/services/{id}/model-version", "some-service")
                        .with(jwt().authorities(TRACK_READ))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownServiceIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/ai/services/{id}/model-version", "no-such-service")
                        .with(jwt().authorities(AI_CONFIGURE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelVersion":"1.3.0","canaryVersion":"","canaryPercentage":0}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNonSemanticVersionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/ai/services/{id}/model-version", "some-service")
                        .with(jwt().authorities(AI_CONFIGURE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelVersion":"latest","canaryVersion":"","canaryPercentage":0}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCanaryPercentageOutsideZeroToOneHundredIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/ai/services/{id}/model-version", "some-service")
                        .with(jwt().authorities(AI_CONFIGURE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelVersion":"1.2.0","canaryVersion":"1.3.0","canaryPercentage":150}"""))
                .andExpect(status().isBadRequest());
    }
}
