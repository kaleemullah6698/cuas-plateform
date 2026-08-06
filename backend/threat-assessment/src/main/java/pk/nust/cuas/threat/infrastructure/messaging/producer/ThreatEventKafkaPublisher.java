package pk.nust.cuas.threat.infrastructure.messaging.producer;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.threat.common.ThreatProtoMapper;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatLevelTransition;
import pk.nust.cuas.threat.domain.port.ThreatEventPublisher;
import pk.nust.cuas.threat.infrastructure.messaging.ThreatTopics;

/**
 * Kafka adapter publishing this service's three topics (ARCHITECTURE.md sections 4.1.7, 5.2, 5.3.3).
 *
 * <p>All three are keyed by <strong>track id</strong>, including the recommendation. A track's
 * assessments, its level changes and its advice therefore share a partition and stay in order
 * relative to each other. Keying the recommendation by its own id would have been the obvious
 * choice and would have let a consumer see advice for a level transition it had not yet observed.
 *
 * <p><strong>Publication is synchronous and failure propagates.</strong> The send blocks on the
 * broker acknowledgement, so a failure rolls back the surrounding transaction and the assessment is
 * not persisted — leaving the triggering record to be redelivered and scored again. That is the
 * opposite of the orchestrator's logged-and-dropped sends, and deliberately so: there, the caller
 * already holds its answer and the event is telemetry; here the event <em>is</em> the output. A
 * stored assessment nothing downstream ever hears about is a threat nobody is alerted to, which is
 * exactly the outcome this service exists to prevent.
 */
@Component
public class ThreatEventKafkaPublisher implements ThreatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ThreatEventKafkaPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafka;
    private final ThreatProtoMapper mapper;
    private final Uuidv7Generator ids;
    private final Clock clock;

    public ThreatEventKafkaPublisher(KafkaTemplate<String, byte[]> kafka, ThreatProtoMapper mapper,
                                     Uuidv7Generator ids, Clock clock) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public void publishAssessed(ThreatAssessment assessment) {
        send(ThreatTopics.THREAT_ASSESSED, assessment.trackId().toString(),
                mapper.toProto(assessment).toByteArray());
        log.debug("Published threat.assessed for track {} at {}",
                assessment.trackId(), assessment.compositeScore());
    }

    @Override
    public void publishLevelChanged(ThreatLevelTransition transition) {
        send(ThreatTopics.THREAT_LEVEL_CHANGED, transition.trackId().toString(),
                mapper.toProto(transition).toByteArray());
        log.debug("Published threat.level.changed for track {} -> {}",
                transition.trackId(), transition.currentLevel());
    }

    @Override
    public void publishRecommendation(ThreatAssessment assessment) {
        byte[] payload = mapper
                .toRecommendationProto(assessment, ids.generate(Instant.now(clock)))
                .toByteArray();
        send(ThreatTopics.THREAT_RECOMMENDATION_GENERATED, assessment.trackId().toString(), payload);
        log.debug("Published threat.recommendation.generated for track {}: {}",
                assessment.trackId(), assessment.recommendation().recommendation());
    }

    private void send(String topic, String key, byte[] payload) {
        try {
            kafka.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing to " + topic, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not publish to " + topic, ex);
        }
    }
}
