package pk.nust.cuas.threat.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.threat.application.ThreatAssessmentService;
import pk.nust.cuas.threat.common.ThreatProtoMapper;
import pk.nust.cuas.threat.infrastructure.messaging.ThreatTopics;
import pk.nust.cuas.track.v1.Track;

/**
 * Consumes {@code track.updated.v1} and re-scores the track (ARCHITECTURE.md section 4.1.7).
 *
 * <p>The kinematic half of the input. Every update re-scores, because proximity, closure and intent
 * all move with the track — a score computed once at track creation would describe a drone that has
 * since arrived.
 *
 * <p><b>A malformed message is acknowledged, not retried.</b> A record that cannot be parsed will
 * not parse on the next attempt either, and blocking the partition on it would stop every later
 * assessment behind one poison record — the failure section 5.6 exists to prevent. DLQ routing is
 * the M8 hardening item recorded for the pipeline's other consumers, and the topic's DLQ is already
 * provisioned.
 */
@Component
public class TrackUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrackUpdatedConsumer.class);

    private final ThreatAssessmentService assessments;
    private final ThreatProtoMapper mapper;

    public TrackUpdatedConsumer(ThreatAssessmentService assessments, ThreatProtoMapper mapper) {
        this.assessments = assessments;
        this.mapper = mapper;
    }

    @KafkaListener(topics = ThreatTopics.TRACK_UPDATED,
            groupId = "${spring.kafka.consumer.group-id:threat-assessment}")
    public void onTrackUpdated(ConsumerRecord<String, byte[]> record) {
        try {
            Track track = Track.parseFrom(record.value());
            mapper.toSnapshot(track).ifPresentOrElse(
                    assessments::onTrackUpdate,
                    () -> log.warn("Skipping track update with an unusable track id at offset {}",
                            record.offset()));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping unparseable track update at offset {}", record.offset(), ex);
        }
    }
}
