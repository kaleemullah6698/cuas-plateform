package pk.nust.cuas.threat.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pk.nust.cuas.commons.audit.AuditWriteException;
import pk.nust.cuas.threat.api.dto.EvaluateRequest;
import pk.nust.cuas.threat.api.dto.PolicyUpdateRequest;
import pk.nust.cuas.threat.application.ThreatPolicyService;
import pk.nust.cuas.threat.application.ThreatQueryService;
import pk.nust.cuas.threat.domain.exception.AssessmentNotFoundException;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;
import pk.nust.cuas.threat.domain.service.RecommendationEngine;
import pk.nust.cuas.threat.domain.service.ThreatScorer;

/**
 * The HTTP surface of section 4.1.7, with section 7.4's permissions enforced.
 *
 * <p>The authorisation assertions are the point of this class. Section 7.4 makes
 * {@code @PreAuthorize} the authoritative layer, so every endpoint is exercised both with and
 * without its permission — a UI that hides a button is convenience, and the server is the control.
 */
@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(
        controllers = ThreatController.class)
@Import({ThreatExceptionHandler.class, ThreatControllerTest.Config.class,
        pk.nust.cuas.threat.config.SecurityConfig.class})
class ThreatControllerTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");
    private static final UUID TRACK = UUID.fromString("018f2b3c-4d5e-7f80-9a1b-2c3d4e5f6071");

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.fixed(T, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private ThreatQueryService threats;

    @MockitoBean
    private ThreatPolicyService policies;

    // ── Reads ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void anAssessmentIsReadableWithTrackRead() throws Exception {
        when(threats.latest(TRACK)).thenReturn(assessment());

        mvc.perform(get("/threats/{id}", TRACK).with(jwt().authorities(authority("track:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackId").value(TRACK.toString()))
                .andExpect(jsonPath("$.policyVersion").value("policy-v1"))
                .andExpect(jsonPath("$.recommendation").isNotEmpty());
    }

    @Test
    void readingAnAssessmentWithoutTrackReadIsForbidden() throws Exception {
        mvc.perform(get("/threats/{id}", TRACK).with(jwt().authorities(authority("alert:read"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void theForbiddenResponseDoesNotNameTheMissingPermission() throws Exception {
        // Naming it would let a caller map the authorisation model one request at a time.
        mvc.perform(get("/threats/{id}", TRACK).with(jwt().authorities(authority("alert:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "You do not have permission to perform this action."));
    }

    @Test
    void anUnscoredTrackIsFourOhFourNotAZeroScore() throws Exception {
        // "Never scored" and "scored zero" are different facts; returning the second for the first
        // would tell an operator the system had judged a track harmless when it had not judged it.
        when(threats.latest(TRACK)).thenThrow(new AssessmentNotFoundException(TRACK));

        mvc.perform(get("/threats/{id}", TRACK).with(jwt().authorities(authority("track:read"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aMalformedTrackIdIsFourHundred() throws Exception {
        mvc.perform(get("/threats/{id}", "not-a-uuid")
                        .with(jwt().authorities(authority("track:read"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theBreakdownCarriesEveryFactorIncludingTheAbsentOnes() throws Exception {
        when(threats.latest(TRACK)).thenReturn(assessment());

        mvc.perform(get("/threats/{id}/breakdown", TRACK)
                        .with(jwt().authorities(authority("track:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factors.length()").value(5))
                // An unavailable factor reports null, never a zero a client could mistake for a
                // measurement.
                .andExpect(jsonPath("$.factors[?(@.available == false)].value").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.factors[?(@.available == false)].unavailableReason")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString()))));
    }

    // ── Policy ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void policiesAreReadableWithPolicyRead() throws Exception {
        when(policies.versions()).thenReturn(List.of(policy()));

        mvc.perform(get("/threats/policies").with(jwt().authorities(authority("policy:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value("policy-v1"))
                .andExpect(jsonPath("$[0].weights.MODEL_CONFIDENCE").value(0.40));
    }

    @Test
    void readingPoliciesWithoutPolicyReadIsForbidden() throws Exception {
        mvc.perform(get("/threats/policies").with(jwt().authorities(authority("track:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishingAPolicyRequiresPolicyWriteNotMerelyPolicyRead() throws Exception {
        // Section 7.4 grants policy:write to ADMIN alone, and rightly: republishing a policy changes
        // what the system concludes about every track in the airspace.
        mvc.perform(put("/threats/policies")
                        .with(jwt().authorities(authority("policy:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validPolicyRequest())))
                .andExpect(status().isForbidden());

        verify(policies, never()).publish(any(), anyString(), anyString());
    }

    @Test
    void publishingAPolicyWithPolicyWriteSucceedsAndCarriesTheJustification() throws Exception {
        when(policies.publish(any(), anyString(), anyString())).thenReturn(policy());

        mvc.perform(put("/threats/policies")
                        .with(jwt().authorities(authority("policy:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validPolicyRequest())))
                .andExpect(status().isOk());

        verify(policies).publish(any(), anyString(), eq("Retuning after the July exercise"));
    }

    @Test
    void aPolicyWithNoJustificationIsRejected() throws Exception {
        // The justification is written to the audit log; a change nobody explained is a change
        // nobody can review.
        PolicyUpdateRequest unjustified = new PolicyUpdateRequest(
                "policy-v2", "  ", weightsMap(), 0.30, 0.55, 0.80,
                200.0, 5000.0, 18.0, 4, 0.50, List.of(validRule()));

        mvc.perform(put("/threats/policies")
                        .with(jwt().authorities(authority("policy:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(unjustified)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPolicyWhoseWeightsDoNotSumToOneIsUnprocessable() throws Exception {
        Map<String, Double> broken = new java.util.HashMap<>(weightsMap());
        broken.put("PROXIMITY", 0.45);
        PolicyUpdateRequest request = new PolicyUpdateRequest(
                "policy-v2", "Retuning", broken, 0.30, 0.55, 0.80,
                200.0, 5000.0, 18.0, 4, 0.50, List.of(validRule()));

        mvc.perform(put("/threats/policies")
                        .with(jwt().authorities(authority("policy:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("sum to 1.0")));
    }

    @Test
    void aRuleNamingAnUnknownRecommendationIsRejectedAtTheBoundary() throws Exception {
        // The check that keeps the advisory-only scope limit intact through an API that otherwise
        // lets an administrator rewrite what the system advises.
        PolicyUpdateRequest request = new PolicyUpdateRequest(
                "policy-v2", "Retuning", weightsMap(), 0.30, 0.55, 0.80,
                200.0, 5000.0, 18.0, 4, 0.50,
                List.of(new PolicyUpdateRequest.RuleRequest("rogue", 10, "LOW", false, "ANY",
                        false, 0, "JAM_CONTROL_LINK", "")));

        mvc.perform(put("/threats/policies")
                        .with(jwt().authorities(authority("policy:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        verify(policies, never()).publish(any(), anyString(), anyString());
    }

    @Test
    void aPolicyChangeThatCannotBeAuditedIsNotApplied() throws Exception {
        // Section 7.5's fail-closed rule, surfaced as 503: the request was valid and should be
        // retried once the audit log is reachable, which a 500 would not communicate.
        when(policies.publish(any(), anyString(), anyString()))
                .thenThrow(new AuditWriteException("audit log unreachable", null));

        mvc.perform(put("/threats/policies")
                        .with(jwt().authorities(authority("policy:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validPolicyRequest())))
                .andExpect(status().isServiceUnavailable())
                // The machine-readable code, not the title: ProblemDetailFactory sets title to the
                // status reason phrase, and `code` is the stable identifier a client branches on.
                .andExpect(jsonPath("$.code").value("audit-unavailable"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("audit log")));
    }

    // ── What-if ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void theWhatIfIsAReadAndNeedsOnlyPolicyRead() throws Exception {
        // Requiring policy:write would mean an operator had to be able to change a policy in order
        // to ask what one would do.
        when(policies.evaluate(any(), any())).thenReturn(assessment());

        mvc.perform(post("/threats/evaluate")
                        .with(jwt().authorities(authority("policy:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(evaluateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factors.length()").value(5));
    }

    @Test
    void theWhatIfWithoutPolicyReadIsForbidden() throws Exception {
        mvc.perform(post("/threats/evaluate")
                        .with(jwt().authorities(authority("track:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(evaluateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWhatIfWithNoSpeedIsRejected() throws Exception {
        mvc.perform(post("/threats/evaluate")
                        .with(jwt().authorities(authority("policy:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private static SimpleGrantedAuthority authority(String permission) {
        return new SimpleGrantedAuthority(permission);
    }

    private static ThreatPolicy policy() {
        return ThreatPolicy.defaultPolicy(T.minusSeconds(3600));
    }

    private static ThreatAssessment assessment() {
        TrackEvidence evidence = TrackEvidence.empty().with(new TrackSnapshot(
                TRACK, "sector-NE", T, 14.0,
                Optional.of(new AssetApproach("SITE-1", "Site 1", 640.0, 47.0, true)), "corr-1"));
        return new ThreatScorer(new RecommendationEngine(), new pk.nust.cuas.commons.id
                .Uuidv7Generator(new Random(11))).score(TRACK, evidence, policy(), T);
    }

    private static Map<String, Double> weightsMap() {
        return Map.of(
                "MODEL_CONFIDENCE", 0.40, "PROXIMITY", 0.25, "CLOSING_VELOCITY", 0.20,
                "TRAJECTORY_INTENT", 0.10, "SENSOR_AGREEMENT", 0.05);
    }

    private static PolicyUpdateRequest.RuleRequest validRule() {
        return new PolicyUpdateRequest.RuleRequest(
                "routine", 10, "LOW", false, "ANY", false, 0, "MONITOR", "Routine observation.");
    }

    private static PolicyUpdateRequest validPolicyRequest() {
        return new PolicyUpdateRequest(
                "policy-v2", "Retuning after the July exercise", weightsMap(),
                0.30, 0.55, 0.80, 200.0, 5000.0, 18.0, 4, 0.50, List.of(validRule()));
    }

    private static EvaluateRequest evaluateRequest() {
        return new EvaluateRequest(null, "sector-NE", 14.0,
                new EvaluateRequest.ApproachRequest("SITE-1", "Site 1", 640.0, 47.0, true),
                3, List.of(new EvaluateRequest.ObservationRequest(
                        "model-a", "1.3.0", "Mavic3", "DRONE", 0.91)));
    }
}
