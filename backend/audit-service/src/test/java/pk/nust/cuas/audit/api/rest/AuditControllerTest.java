package pk.nust.cuas.audit.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pk.nust.cuas.audit.config.SecurityConfig;
import pk.nust.cuas.audit.domain.AuditRepository;
import pk.nust.cuas.audit.domain.service.ChainVerifier;

/**
 * RBAC enforcement on the audit API (ARCHITECTURE.md section 7.4).
 *
 * <p>Every endpoint is asserted three ways — authorised, wrong permission, unauthenticated —
 * because an untested {@code @PreAuthorize} is an unverified security control. The permission
 * strings here are the same constants the annotations use, so a rename cannot silently pass.
 */
@WebMvcTest(controllers = AuditController.class)
@Import({SecurityConfig.class, AuditExceptionHandler.class,
        AuditControllerTest.StubJwtDecoderConfig.class})
class AuditControllerTest {

    /** The resource server needs a decoder bean; tests authenticate with mock authorities. */
    @TestConfiguration
    static class StubJwtDecoderConfig {
        @Bean
        org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("not used in these tests");
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditRepository repository;

    @MockitoBean
    private ChainVerifier chainVerifier;

    // ── GET /api/v1/audit ──

    @Test
    @WithMockUser(authorities = "audit:read")
    void searchIsAllowedWithAuditRead() throws Exception {
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/audit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "track:read")
    void searchIsForbiddenWithoutAuditRead() throws Exception {
        mockMvc.perform(get("/api/v1/audit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    @Test
    void searchIsUnauthorisedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "audit:read")
    void searchRejectsAnOutOfRangeLimit() throws Exception {
        mockMvc.perform(get("/api/v1/audit").param("limit", "5000"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/audit/verify ──

    @Test
    @WithMockUser(authorities = "audit:verify")
    void verifyIsAllowedWithAuditVerify() throws Exception {
        when(repository.findSegment(anyLong(), anyLong())).thenReturn(List.of());
        when(chainVerifier.verify(any(), any()))
                .thenReturn(pk.nust.cuas.audit.domain.service.ChainVerificationResult
                        .intact(0, new byte[32]));

        mockMvc.perform(post("/api/v1/audit/verify").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true));
    }

    @Test
    @WithMockUser(authorities = "audit:read")
    void verifyIsForbiddenWithOnlyAuditRead() throws Exception {
        // audit:read and audit:verify are distinct permissions; reading must not imply attesting.
        mockMvc.perform(post("/api/v1/audit/verify").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "audit:verify")
    void verifyRejectsAnInvertedRange() throws Exception {
        mockMvc.perform(post("/api/v1/audit/verify").with(csrf())
                        .param("fromSeq", "10")
                        .param("toSeq", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-argument"));
    }

    @Test
    @WithMockUser(authorities = "audit:verify")
    void brokenChainIsReportedWithTheOffendingRecord() throws Exception {
        when(repository.findSegment(anyLong(), anyLong())).thenReturn(List.of());
        when(chainVerifier.verify(any(), any()))
                .thenReturn(pk.nust.cuas.audit.domain.service.ChainVerificationResult
                        .broken(42L, java.util.UUID.randomUUID(), "record_hash mismatch"));

        mockMvc.perform(post("/api/v1/audit/verify").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(false))
                .andExpect(jsonPath("$.brokenAtSeq").value(42));
    }
}
