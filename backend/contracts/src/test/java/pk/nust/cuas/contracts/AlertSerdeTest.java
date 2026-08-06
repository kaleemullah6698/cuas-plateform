package pk.nust.cuas.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.alert.v1.AlertAcknowledged;
import pk.nust.cuas.alert.v1.AlertDisposition;
import pk.nust.cuas.alert.v1.AlertEscalated;
import pk.nust.cuas.alert.v1.AlertResolved;
import pk.nust.cuas.alert.v1.AlertStatus;
import pk.nust.cuas.alert.v1.DecisionChain;
import pk.nust.cuas.alert.v1.EngagementAction;
import pk.nust.cuas.alert.v1.EscalationTrigger;
import pk.nust.cuas.alert.v1.EvidenceKind;
import pk.nust.cuas.alert.v1.EvidenceRef;
import pk.nust.cuas.alert.v1.Incident;
import pk.nust.cuas.alert.v1.IncidentEvent;
import pk.nust.cuas.alert.v1.IncidentStatus;
import pk.nust.cuas.threat.v1.Recommendation;
import pk.nust.cuas.threat.v1.ThreatLevel;

/**
 * Serialization round-trip coverage for every message in {@code alert.proto}
 * (ARCHITECTURE.md sections 5.3.4, 4.1.8, 2.4.3), including the cross-package reuse of
 * {@code threat.v1.ThreatLevel} as alert severity and the closed {@link EngagementAction}
 * vocabulary that carries the platform's hard scope limit onto the wire.
 */
class AlertSerdeTest {

    @Test
    void alertRoundTripsWithAllFields() throws Exception {
        Alert original = Alert.newBuilder()
                .setAlertId("01J8X4K3N2R5T1").setReference("ALT-9912")
                .setStatus(AlertStatus.ALERT_STATUS_RAISED)
                .setDedupKey("TRK-0473|CRITICAL|policy-v3")
                .setSeverity(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .setRecommendation(Recommendation.TRACK_AND_MONITOR)
                .setThreatScore(0.853f)
                .setTrackId("TRK-0473").setSectorId("sector-NE")
                .setAssessmentId("01J8X4K3N2R5S8")
                .setTitle("CRITICAL — TRK-0473 closing on SITE-1")
                .setSummary("CPA 640 m in 00:47, 3 of 3 modalities agree")
                .setRaisedAt(Timestamp.newBuilder().setSeconds(1_784_562_300).build())
                .setEscalationLevel(0)
                .setEscalatesAt(Timestamp.newBuilder().setSeconds(1_784_562_420).build())
                .setOccurrenceCount(1)
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();

        Alert parsed = Alert.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getSeverity()).isEqualTo(ThreatLevel.THREAT_LEVEL_CRITICAL);
        assertThat(parsed.getDedupKey()).isEqualTo("TRK-0473|CRITICAL|policy-v3");
    }

    @Test
    void suppressedRepeatIsOneAlertWithAnOccurrenceCount() throws Exception {
        // Section 5.7: duplicate alerts destroy operator trust faster than any other failure.
        // A recurrence inside the suppression window increments the open alert rather than
        // raising a second one — but the count keeps suppression from also hiding intensity.
        Alert original = Alert.newBuilder()
                .setAlertId("01J8X4K3N2R5T1").setDedupKey("TRK-0473|CRITICAL|policy-v3")
                .setStatus(AlertStatus.ALERT_STATUS_RAISED)
                .setOccurrenceCount(7)
                .build();

        assertThat(Alert.parseFrom(original.toByteArray()).getOccurrenceCount()).isEqualTo(7);
    }

    @Test
    void acknowledgementNamesTheActor() throws Exception {
        AlertAcknowledged original = AlertAcknowledged.newBuilder()
                .setAlertId("01J8X4K3N2R5T1").setReference("ALT-9912").setTrackId("TRK-0473")
                .setActorId("usr-4411").setActorDisplayName("K.Ullah")
                .setNote("visual confirms rotary-wing")
                .setAcknowledgedAt(Timestamp.newBuilder().setSeconds(1_784_562_341).build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();

        AlertAcknowledged parsed = AlertAcknowledged.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        // An acknowledgement stops the escalation timer, so the audit chain must be able to
        // answer who silenced the alert.
        assertThat(parsed.getActorId()).isNotBlank();
    }

    @Test
    void escalationRoundTripsForEveryTrigger() throws Exception {
        for (EscalationTrigger trigger : EscalationTrigger.values()) {
            if (trigger == EscalationTrigger.UNRECOGNIZED) {
                continue;
            }
            AlertEscalated original = AlertEscalated.newBuilder()
                    .setAlertId("01J8X4K3N2R5T1").setReference("ALT-9912").setTrackId("TRK-0473")
                    .setPreviousLevel(0).setCurrentLevel(1)
                    .setTrigger(trigger)
                    .setSeverity(ThreatLevel.THREAT_LEVEL_CRITICAL)
                    .setEscalatedAt(Timestamp.newBuilder().setSeconds(1_784_562_420).build())
                    .setNextEscalateAt(Timestamp.newBuilder().setSeconds(1_784_562_720).build())
                    .build();

            assertThat(AlertEscalated.parseFrom(original.toByteArray())).isEqualTo(original);
        }
    }

    @Test
    void resolutionDistinguishesAnOperatorVerdictFromAnAutoClose() throws Exception {
        AlertResolved byOperator = AlertResolved.newBuilder()
                .setAlertId("01J8X4K3N2R5T1").setReference("ALT-9912").setTrackId("TRK-0473")
                .setDisposition(AlertDisposition.ALERT_DISPOSITION_TRUE_POSITIVE)
                .setNote("escalated to INC-2026-0087")
                .setActorId("usr-4411").setActorDisplayName("K.Ullah")
                .setAutoResolved(false)
                .setResolvedAt(Timestamp.newBuilder().setSeconds(1_784_563_000).build())
                .build();
        AlertResolved bySystem = AlertResolved.newBuilder()
                .setAlertId("01J8X4K3N2R5T2")
                .setDisposition(AlertDisposition.ALERT_DISPOSITION_INCONCLUSIVE)
                .setAutoResolved(true)
                .build();

        assertThat(AlertResolved.parseFrom(byOperator.toByteArray())).isEqualTo(byOperator);
        assertThat(AlertResolved.parseFrom(bySystem.toByteArray())).isEqualTo(bySystem);
        // An auto-closed alert is not evidence that anybody looked at it; the false-positive
        // rate computed from dispositions would be wrong if the two were conflated.
        assertThat(bySystem.getActorId()).isEmpty();
        assertThat(bySystem.getAutoResolved()).isTrue();
    }

    @Test
    void incidentRoundTripsWithItsDecisionChain() throws Exception {
        Incident original = Incident.newBuilder()
                .setIncidentId("01J8X4K3N2R5U1").setReference("INC-2026-0087")
                .setStatus(IncidentStatus.INCIDENT_STATUS_UNDER_REVIEW)
                .setSeverity(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .setTitle("Unauthorised UAS — Sector NE").setSectorId("sector-NE")
                .addTrackIds("TRK-0473").addAlertIds("01J8X4K3N2R5T1")
                .setDeclaredBy("usr-4411").setDeclaredByName("K.Ullah")
                .setAssignedTo("usr-2210").setAssignedToName("S.R.E.Shah")
                .setOpenedAt(Timestamp.newBuilder().setSeconds(1_784_562_063).build())
                .setDecisionChain(DecisionChain.newBuilder()
                        .setSystemRecommended(Recommendation.TRACK_AND_MONITOR)
                        .setOperatorDecision("Escalate to incident")
                        .setAuthorisedBy("usr-4411").setAuthorisedByName("K.Ullah")
                        .setAuthorisedAt(Timestamp.newBuilder().setSeconds(1_784_562_250).build())
                        .setAuditRef("audit:0x8f3a")
                        .setEngagementAction(EngagementAction.ENGAGEMENT_ACTION_NONE)
                        .build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();

        Incident parsed = Incident.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        // An incident is always declared by a person (section 2.4.3, "OPERATOR DECIDED").
        assertThat(parsed.getDeclaredBy()).isNotBlank();
        // The operator's decision may differ from the advice — that divergence is the reason
        // the chain is recorded at all.
        assertThat(parsed.getDecisionChain().getOperatorDecision())
                .isNotEqualTo(parsed.getDecisionChain().getSystemRecommended().name());
    }

    @Test
    void incidentEventDistinguishesASystemEntryFromAnOperatorEntry() throws Exception {
        IncidentEvent system = IncidentEvent.newBuilder()
                .setIncidentId("01J8X4K3N2R5U1").setEventId("01J8X4K3N2R5U2")
                .setSourceTopic("threat.level.changed.v1").setSourceId("TRK-0473")
                .setSummary("threat MED → CRITICAL 0.853")
                .setOccurredAt(Timestamp.newBuilder().setSeconds(1_784_562_186).build())
                .build();
        IncidentEvent operator = system.toBuilder()
                .setEventId("01J8X4K3N2R5U3")
                .setSourceTopic("alert.acknowledged.v1")
                .setSummary("K.Ullah acknowledged")
                .setActorId("usr-4411").setActorDisplayName("K.Ullah")
                .build();

        assertThat(IncidentEvent.parseFrom(system.toByteArray())).isEqualTo(system);
        assertThat(IncidentEvent.parseFrom(operator.toByteArray())).isEqualTo(operator);
        // Empty actor is what renders as "SYS" rather than "USER" in section 2.4.3.
        assertThat(system.getActorId()).isEmpty();
        assertThat(operator.getActorId()).isNotBlank();
    }

    @Test
    void evidenceIsReferencedNeverInlined() throws Exception {
        EvidenceRef original = EvidenceRef.newBuilder()
                .setEvidenceId("01J8X4K3N2R5V1").setIncidentId("01J8X4K3N2R5U1")
                .setKind(EvidenceKind.EVIDENCE_KIND_FRAME)
                .setUri("evidence://incidents/01J8X4K3N2R5U1/eo-01-142641220.jpg")
                .setSha256("4a7fc21e9b3d5c8e17f2a4b6c8d0e2f4a6b8c0d2e4f6a8b0c2d4e6f8a0b2c4d6")
                .setSizeBytes(184_320).setContentType("image/jpeg")
                .setSourceId("EO-01")
                .setCapturedAt(Timestamp.newBuilder().setSeconds(1_784_562_401).build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();

        EvidenceRef parsed = EvidenceRef.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        // The contract addresses the object and hashes it; the payload never travels on a topic
        // the gateway, reporting and audit all consume.
        assertThat(parsed.getUri()).isNotBlank();
        assertThat(parsed.getSha256()).hasSize(64);
        assertThat(EvidenceRef.getDescriptor().getFields())
                .noneMatch(f -> f.getJavaType() == com.google.protobuf.Descriptors.FieldDescriptor
                        .JavaType.BYTE_STRING);
    }

    @Test
    void engagementActionHasExactlyOneLegalValueAndItIsNone() {
        // CLAUDE.md's hard scope limit, asserted structurally rather than in prose: this
        // platform emits advisory recommendations only, and no producer can set a value on the
        // decision chain that claims an engagement action was taken. Adding a value here is an
        // architecture amendment with supervisor approval — this test is what makes that true
        // of the build rather than only of the document.
        assertThat(EngagementAction.values())
                .filteredOn(a -> a != EngagementAction.UNRECOGNIZED)
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "ENGAGEMENT_ACTION_UNSPECIFIED",
                        "ENGAGEMENT_ACTION_NONE");
    }

    @Test
    void alertSeverityReusesTheThreatLevelScale() {
        // A parallel alert severity scale would be a second source of truth for one fact and
        // would diverge the first time a section 6.3.4 threshold moved.
        assertThat(Alert.getDescriptor().findFieldByName("severity").getEnumType().getFullName())
                .isEqualTo(ThreatLevel.getDescriptor().getFullName());
    }

    @Test
    void allAlertStatusAndDispositionValuesPreserved() throws Exception {
        for (AlertStatus status : AlertStatus.values()) {
            if (status == AlertStatus.UNRECOGNIZED) {
                continue;
            }
            Alert original = Alert.newBuilder().setStatus(status).build();
            assertThat(Alert.parseFrom(original.toByteArray()).getStatus()).isEqualTo(status);
        }
        for (AlertDisposition disposition : AlertDisposition.values()) {
            if (disposition == AlertDisposition.UNRECOGNIZED) {
                continue;
            }
            AlertResolved original =
                    AlertResolved.newBuilder().setDisposition(disposition).build();
            assertThat(AlertResolved.parseFrom(original.toByteArray()).getDisposition())
                    .isEqualTo(disposition);
        }
    }

    @Test
    void allEvidenceKindValuesPreserved() throws Exception {
        for (EvidenceKind kind : EvidenceKind.values()) {
            if (kind == EvidenceKind.UNRECOGNIZED) {
                continue;
            }
            EvidenceRef original = EvidenceRef.newBuilder().setKind(kind).build();
            assertThat(EvidenceRef.parseFrom(original.toByteArray()).getKind()).isEqualTo(kind);
        }
    }
}
