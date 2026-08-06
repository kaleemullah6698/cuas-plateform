package pk.nust.cuas.threat.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.ThreatFactor;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.infrastructure.persistence.mapper.AssessmentDocuments.ApproachRow;
import pk.nust.cuas.threat.infrastructure.persistence.mapper.AssessmentDocuments.EvidenceDocument;
import pk.nust.cuas.threat.infrastructure.persistence.mapper.AssessmentDocuments.FactorRow;
import pk.nust.cuas.threat.infrastructure.persistence.mapper.AssessmentDocuments.FactorsDocument;
import pk.nust.cuas.threat.infrastructure.persistence.mapper.AssessmentDocuments.ObservationRow;
import pk.nust.cuas.threat.infrastructure.persistence.mapper.AssessmentDocuments.SnapshotRow;

/**
 * Converts the two JSONB columns of an assessment between their stored form and the domain models.
 *
 * <p>Uses its own {@link ObjectMapper} rather than the application's. The shared mapper is
 * configured for the HTTP surface and can be reconfigured by any starter added later; a persistence
 * format that moves when an unrelated dependency changes is a format that quietly stops reading rows
 * written last month.
 *
 * <p><strong>Unknown properties are ignored on read.</strong> A row written by a later version of
 * this service carrying a field this one does not know about must still be readable — the history is
 * immutable, so a rolled-back deployment has to be able to read forward-written rows or it cannot
 * serve the read API at all. Unknown <em>enum</em> values are the exception and do fail, because a
 * factor type this build does not recognise cannot be weighted or rendered, and silently dropping it
 * would understate the breakdown an operator is reading.
 */
@Component
public class AssessmentJsonCodec {

    private final ObjectMapper json = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature
                    .FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * @param factors the per-factor breakdown
     * @return its JSON form
     */
    public String writeFactors(List<ThreatFactor> factors) {
        return write(new FactorsDocument(factors.stream()
                .map(f -> new FactorRow(
                        f.type().name(),
                        f.value().orElse(null),
                        f.weight(),
                        f.unavailableReason().orElse(null)))
                .toList()));
    }

    /**
     * @param stored the JSON written by {@link #writeFactors}
     * @return the breakdown
     */
    public List<ThreatFactor> readFactors(String stored) {
        FactorsDocument document = read(stored, FactorsDocument.class);
        return document.factors().stream()
                .map(row -> new ThreatFactor(
                        ThreatFactorType.valueOf(row.type()),
                        Optional.ofNullable(row.value()),
                        row.weight(),
                        Optional.ofNullable(row.unavailableReason())))
                .toList();
    }

    /**
     * @param evidence the scoring inputs
     * @return their JSON form
     */
    public String writeEvidence(TrackEvidence evidence) {
        return write(new EvidenceDocument(
                evidence.snapshot().map(AssessmentJsonCodec::toRow).orElse(null),
                evidence.observations().stream().map(AssessmentJsonCodec::toRow).toList(),
                evidence.consecutiveClosingCount()));
    }

    /**
     * @param stored the JSON written by {@link #writeEvidence}
     * @return the inputs
     */
    public TrackEvidence readEvidence(String stored) {
        EvidenceDocument document = read(stored, EvidenceDocument.class);
        Map<String, InferenceObservation> observations = new LinkedHashMap<>();
        for (ObservationRow row : document.observations()) {
            observations.put(row.modelId(), new InferenceObservation(
                    row.modelId(), row.modelVersion(), row.label(), row.labelClass(),
                    row.confidence(), Instant.ofEpochMilli(row.reportedAtMillis())));
        }
        return new TrackEvidence(
                Optional.ofNullable(document.snapshot()).map(AssessmentJsonCodec::toSnapshot),
                observations,
                document.consecutiveClosingCount());
    }

    private static SnapshotRow toRow(TrackSnapshot snapshot) {
        return new SnapshotRow(
                snapshot.trackId().toString(),
                snapshot.sectorId(),
                snapshot.observedAt().toEpochMilli(),
                snapshot.speedMs(),
                snapshot.approach().map(AssessmentJsonCodec::toRow).orElse(null),
                snapshot.correlationId());
    }

    private static ApproachRow toRow(AssetApproach approach) {
        return new ApproachRow(approach.assetId(), approach.assetName(),
                approach.distanceM(), approach.timeToCpaS(), approach.closing());
    }

    private static ObservationRow toRow(InferenceObservation observation) {
        return new ObservationRow(observation.modelId(), observation.modelVersion(),
                observation.label(), observation.labelClass(), observation.confidence(),
                observation.reportedAt().toEpochMilli());
    }

    private static TrackSnapshot toSnapshot(SnapshotRow row) {
        return new TrackSnapshot(
                UUID.fromString(row.trackId()),
                row.sectorId(),
                Instant.ofEpochMilli(row.observedAtMillis()),
                row.speedMs(),
                Optional.ofNullable(row.approach()).map(a -> new AssetApproach(
                        a.assetId(), a.assetName(), a.distanceM(), a.timeToCpaS(), a.closing())),
                row.correlationId());
    }

    private String write(Object document) {
        try {
            return json.writeValueAsString(document);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise an assessment document", ex);
        }
    }

    private <T> T read(String stored, Class<T> type) {
        try {
            return json.readValue(stored, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Could not read a stored assessment document; the row is unusable", ex);
        }
    }
}
