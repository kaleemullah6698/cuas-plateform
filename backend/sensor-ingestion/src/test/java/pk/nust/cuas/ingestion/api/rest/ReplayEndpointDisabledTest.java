package pk.nust.cuas.ingestion.api.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The replay endpoint must not exist when the feature is off (its default). The controller is
 * {@code @ConditionalOnProperty}, so with the flag absent it is never registered and the path 404s —
 * there is no disabled-but-present handler that could be reached by mistake.
 */
@WebMvcTest(controllers = ReplayController.class)
class ReplayEndpointDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void replayIsNotExposedWhenDisabled() throws Exception {
        mockMvc.perform(post("/ingest/replay").contentType("application/x-ndjson").content(""))
                .andExpect(status().isNotFound());
    }
}
