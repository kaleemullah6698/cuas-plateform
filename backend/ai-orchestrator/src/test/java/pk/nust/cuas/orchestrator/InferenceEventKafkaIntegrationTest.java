package pk.nust.cuas.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.inference.v1.ComputeDevice;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.orchestrator.application.InferenceOrchestrator;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;

/**
 * The orchestrator end to end against real Kafka (ARCHITECTURE.md sections 4.1.6, 5.2, 6.5).
 *
 * <p>Drives a real request through routing, the resilience layer and the mock provider, and asserts
 * the four topics carry what §5.2 says they should. The registry is configured here rather than
 * inherited, so the test states its own routing and nothing depends on the shipped example services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ai.services.service-one.modalities=RF",
        "ai.services.service-one.mode=mock",
        "ai.services.service-one.timeout=PT2S",
        "ai.services.service-two.modalities=EO_IR",
        "ai.services.service-two.mode=mock",
        "ai.services.service-two.timeout=PT2S"})
@Testcontainers
class InferenceEventKafkaIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private InferenceOrchestrator orchestrator;

    private static InferenceRequest request(SensorModality modality) {
        return InferenceRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setTrackId("track-" + UUID.randomUUID())
                .setCorrelationId("corr-" + UUID.randomUUID())
                .addDetections(DetectionRecord.newBuilder()
                        .setDetectionId("d1").setSensorId("s1").setSensorSeq(1).setModality(modality))
                .build();
    }

    @Test
    void aRoutedRequestEmitsRequestedAndCompletedForItsService() {
        try (KafkaConsumer<String, byte[]> requested = subscribe("ai.inference.requested.v1");
             KafkaConsumer<String, byte[]> completed = subscribe("ai.inference.completed.v1")) {

            InferenceRequest request = request(SensorModality.MODALITY_RF);
            List<InferenceResult> results = orchestrator.infer(request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).service().value()).isEqualTo("service-one");

            assertThat(awaitKey(requested, request.getTrackId()))
                    .as("keyed by trackId so a track's events stay ordered on one partition")
                    .isTrue();

            InferenceResponse published = awaitResponse(completed, request.getTrackId());
            assertThat(published.getModelId()).isEqualTo("service-one");
            assertThat(published.getDevice())
                    .as("the mock is badged, so nothing downstream can mistake it for a real model")
                    .isEqualTo(ComputeDevice.DEVICE_MOCK);
            assertThat(published.getModelVersion()).startsWith("0.0.0-mock");
        }
    }

    @Test
    void aRequestRoutesOnlyToTheServicesItsModalityMatches() {
        InferenceRequest request = request(SensorModality.MODALITY_EO_IR);

        List<InferenceResult> results = orchestrator.infer(request);

        assertThat(results).extracting(r -> r.service().value()).containsExactly("service-two");
    }

    @Test
    void anUnroutableModalityProducesNothingAndIsNotAnError() {
        assertThat(orchestrator.infer(request(SensorModality.MODALITY_GNSS))).isEmpty();
    }

    @Test
    void anUnusableOutcomeGoesToFailedRatherThanCompleted() {
        // A consumer of `completed` must never have to distinguish "scored zero" from "not scored".
        // The mock's SENSOR_DROPOUT scenario reports INSUFFICIENT_DATA; find a track that draws it.
        try (KafkaConsumer<String, byte[]> failed = subscribe("ai.inference.failed.v1")) {
            InferenceRequest dropout = trackWithUnusableOutcome();

            orchestrator.infer(dropout);

            assertThat(awaitKey(failed, dropout.getTrackId())).isTrue();
        }
    }

    /** Finds a request whose deterministic scenario yields a non-usable status. */
    private InferenceRequest trackWithUnusableOutcome() {
        for (int i = 0; i < 400; i++) {
            InferenceRequest candidate = InferenceRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setTrackId("dropout-probe-" + i)
                    .addDetections(DetectionRecord.newBuilder()
                            .setDetectionId("d" + i).setSensorId("s1").setSensorSeq(i)
                            .setModality(SensorModality.MODALITY_RF))
                    .build();
            List<InferenceResult> results = orchestrator.infer(candidate);
            if (!results.isEmpty()
                    && results.get(0).response().getStatus()
                    == pk.nust.cuas.inference.v1.InferenceStatus.STATUS_INSUFFICIENT_DATA) {
                return candidate;
            }
        }
        throw new IllegalStateException("no track drew an unusable scenario");
    }

    private boolean awaitKey(KafkaConsumer<String, byte[]> consumer, String key) {
        List<String> seen = new ArrayList<>();
        try {
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                for (ConsumerRecord<String, byte[]> record : poll(consumer)) {
                    seen.add(record.key());
                }
                assertThat(seen).contains(key);
            });
            return true;
        } catch (org.awaitility.core.ConditionTimeoutException ex) {
            return false;
        }
    }

    private InferenceResponse awaitResponse(KafkaConsumer<String, byte[]> consumer, String key) {
        List<InferenceResponse> matches = new ArrayList<>();
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            for (ConsumerRecord<String, byte[]> record : poll(consumer)) {
                if (key.equals(record.key())) {
                    matches.add(InferenceResponse.parseFrom(record.value()));
                }
            }
            assertThat(matches).isNotEmpty();
        });
        return matches.get(0);
    }

    private static ConsumerRecords<String, byte[]> poll(KafkaConsumer<String, byte[]> consumer) {
        return consumer.poll(Duration.ofMillis(500));
    }

    private KafkaConsumer<String, byte[]> subscribe(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
