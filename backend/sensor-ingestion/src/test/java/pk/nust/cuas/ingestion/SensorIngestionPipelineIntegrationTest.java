package pk.nust.cuas.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.protobuf.util.JsonFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.ingestion.api.dto.BatchIngestResponse;
import pk.nust.cuas.ingestion.support.Detections;

/**
 * The assembled service against real PostgreSQL, Kafka, and Redis — the guarantees that only hold
 * end to end: a posted detection emerges normalised on {@code sensor.detection.normalized.v1} with
 * platform identity stamped on, the sensordb rows are written, and a repeated sequence number is
 * normalised once and counted as a duplicate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class SensorIngestionPipelineIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("sensordb").withUsername("cuas").withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        // Each test uses a unique sensor id, so the Redis dedup window needs no cross-test cleanup;
        // only the shared database is reset here.
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE sensors, sensor_health, ingestion_stats CASCADE");
    }

    @Test
    void aPostedDetectionEmergesNormalizedOnKafkaAndIsPersisted() throws Exception {
        String sensorId = "rf-" + UUID.randomUUID();
        try (KafkaConsumer<String, byte[]> normalized = subscribe("sensor.detection.normalized.v1")) {
            BatchIngestResponse response = post(Detections.rf(sensorId, 1L, Instant.parse("2026-08-04T12:00:00Z")));
            assertThat(response.accepted()).isEqualTo(1);
            assertThat(response.rejected()).isZero();

            DetectionRecord emitted = awaitNormalized(normalized, sensorId);
            assertThat(emitted.getDetectionId()).isNotBlank();
            assertThat(emitted.hasIngestTime()).isTrue();
            assertThat(emitted.getSchemaVersion()).isEqualTo("1.0.0");
            assertThat(emitted.getModality()).isEqualTo(SensorModality.MODALITY_RF);
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(count("SELECT count(*) FROM sensors WHERE sensor_id = ?", sensorId)).isEqualTo(1);
            assertThat(count("SELECT normalized_total FROM ingestion_stats WHERE sensor_id = ?", sensorId))
                    .isEqualTo(1);
            assertThat(count("SELECT count(*) FROM sensor_health WHERE sensor_id = ?", sensorId)).isEqualTo(1);
        });
    }

    @Test
    void aRepeatedSequenceNumberIsNormalisedOnceAndCountedAsADuplicate() {
        String sensorId = "rf-" + UUID.randomUUID();
        DetectionRecord detection = Detections.rf(sensorId, 99L, Instant.parse("2026-08-04T12:00:00Z"));

        post(detection);
        post(detection); // same (sensorId, sensorSeq)

        // Both raw records are consumed; the second must be recognised as a duplicate.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(count("SELECT received_total FROM ingestion_stats WHERE sensor_id = ?", sensorId))
                        .isEqualTo(2));

        assertThat(count("SELECT normalized_total FROM ingestion_stats WHERE sensor_id = ?", sensorId))
                .isEqualTo(1);
        assertThat(count("SELECT duplicate_total FROM ingestion_stats WHERE sensor_id = ?", sensorId))
                .isEqualTo(1);
    }

    @Test
    void anInvalidDetectionIsRejectedAtIntakeWithoutEnteringThePipeline() {
        String sensorId = "rf-" + UUID.randomUUID();
        DetectionRecord invalid = Detections.rf(sensorId, 1L, Instant.parse("2026-08-04T12:00:00Z"))
                .toBuilder().setModality(SensorModality.MODALITY_UNSPECIFIED).build();

        BatchIngestResponse response = post(invalid);

        assertThat(response.accepted()).isZero();
        assertThat(response.rejected()).isEqualTo(1);
        assertThat(response.rejections()).singleElement()
                .satisfies(r -> assertThat(r.violations()).anyMatch(v -> v.contains("modality")));
    }

    private BatchIngestResponse post(DetectionRecord record) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body;
        try {
            body = "{\"detections\":[" + JsonFormat.printer().print(record) + "]}";
        } catch (Exception ex) {
            throw new IllegalStateException("could not render detection JSON", ex);
        }
        return rest.postForEntity("/ingest/detections", new HttpEntity<>(body, headers),
                BatchIngestResponse.class).getBody();
    }

    private DetectionRecord awaitNormalized(KafkaConsumer<String, byte[]> consumer, String sensorId) {
        List<DetectionRecord> matches = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> record : records) {
                DetectionRecord parsed = DetectionRecord.parseFrom(record.value());
                if (parsed.getSensorId().equals(sensorId)) {
                    matches.add(parsed);
                }
            }
            assertThat(matches).isNotEmpty();
        });
        return matches.get(0);
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

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
