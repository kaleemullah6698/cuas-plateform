package pk.nust.cuas.track;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.track.support.Detections;
import pk.nust.cuas.track.v1.Track;
import pk.nust.cuas.track.v1.TrackStatus;

/**
 * The miss-timeout half of the lifecycle against real PostgreSQL and Kafka (ARCHITECTURE.md section
 * 4.1.5): a confirmed track that stops being fed coasts, then is lost, and a coasting track that a
 * detection reaches again is revived.
 *
 * <p>Separate from {@link TrackCorrelationPipelineIntegrationTest} because the two want opposite
 * things from the sweep — that test suppresses it to assert on stable tentative/confirmed states,
 * this one drives it. Detections are stamped at {@link Instant#now()} rather than a fixed past
 * instant, because the sweep measures staleness against the wall clock: a detection dated in the
 * past would already be past both timeouts when it arrived, and the track would be born lost.
 *
 * <p>The timeouts are widened well beyond the production defaults so the assertions are not racing
 * Kafka delivery — the point is to observe the ordered transitions, not to measure latency. Each
 * test owns a sector, so the sweep's global query cannot make one test's tracks affect another's
 * assertions.
 */
// MOCK, not NONE: the service's SecurityConfig builds a servlet filter chain, so the web context has
// to exist even though this test drives the service entirely through Kafka and never calls the API.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "correlation.lifecycle.coast-timeout=PT8S",
        "correlation.lifecycle.lost-timeout=PT16S",
        "correlation.lifecycle.check-interval=PT1S"})
@Testcontainers
class TrackLifecycleIntegrationTest {

    private static final String NORMALIZED = "sensor.detection.normalized.v1";

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("trackdb").withUsername("cuas").withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void jdbc() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void aConfirmedTrackThatStopsBeingFedCoastsThenIsLost() {
        double lat = 30.5;
        double lon = 40.5;
        String sector = "SECTOR_30_40";

        try (KafkaConsumer<String, byte[]> updates = subscribe("track.updated.v1");
             KafkaConsumer<String, byte[]> losses = subscribe("track.lost.v1")) {

            confirmATrack(sector, lat, lon, "coast");

            // The sweep predicts it forward and republishes it on track.updated.v1 as COASTING.
            awaitStatus(sector, "COASTING", Duration.ofSeconds(20));
            assertThat(awaitTrack(updates, sector, TrackStatus.TRACK_STATUS_COASTING).getDetectionCount())
                    .as("coasting does not consume a detection")
                    .isEqualTo(3);

            // Still not fed: the lost timeout takes it to the terminal state on track.lost.v1.
            awaitStatus(sector, "LOST", Duration.ofSeconds(25));
            assertThat(awaitTrack(losses, sector, TrackStatus.TRACK_STATUS_LOST).getTrackId())
                    .isEqualTo(trackIdIn(sector));
        }

        // Creation, confirmation, coasting, loss — the full ordered chain is persisted.
        assertThat(lifecycleTransitions(sector)).containsExactly(
                "->TENTATIVE", "TENTATIVE->CONFIRMED", "CONFIRMED->COASTING", "COASTING->LOST");
    }

    @Test
    void aDetectionRevivesACoastingTrackRatherThanStartingANewOne() {
        double lat = 31.5;
        double lon = 41.5;
        String sector = "SECTOR_31_41";

        confirmATrack(sector, lat, lon, "revive");
        awaitStatus(sector, "COASTING", Duration.ofSeconds(20));
        String coastingId = trackIdIn(sector);

        // A detection at the same place, while the track is coasting but before it is lost.
        publish(Detections.normalized("revive-4-" + UUID.randomUUID(), "rf-revive", 4L,
                lat, lon, Instant.now()));

        awaitStatus(sector, "CONFIRMED", Duration.ofSeconds(20));
        assertThat(tracksInSector(sector)).as("revived, not duplicated").isEqualTo(1);
        assertThat(trackIdIn(sector)).isEqualTo(coastingId);
        assertThat(number("SELECT detection_count FROM tracks WHERE sector_id = ?", sector)).isEqualTo(4);
        assertThat(lifecycleTransitions(sector)).containsExactly(
                "->TENTATIVE", "TENTATIVE->CONFIRMED", "CONFIRMED->COASTING", "COASTING->CONFIRMED");
    }

    /** Publishes the three detections that take a new track to CONFIRMED, and waits for it. */
    private void confirmATrack(String sector, double lat, double lon, String prefix) {
        Instant now = Instant.now();
        for (int seq = 1; seq <= 3; seq++) {
            publish(Detections.normalized(prefix + '-' + seq + '-' + UUID.randomUUID(),
                    "rf-" + prefix, seq, lat, lon, now.plusMillis(seq * 10L)));
        }
        awaitStatus(sector, "CONFIRMED", Duration.ofSeconds(20));
    }

    private void awaitStatus(String sector, String status, Duration timeout) {
        await().atMost(timeout).untilAsserted(() ->
                assertThat(string("SELECT COALESCE(MAX(status),'') FROM tracks WHERE sector_id = ?", sector))
                        .isEqualTo(status));
    }

    private Track awaitTrack(KafkaConsumer<String, byte[]> consumer, String sector, TrackStatus status) {
        List<Track> matches = new ArrayList<>();
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> record : records) {
                Track track = Track.parseFrom(record.value());
                if (track.getSectorId().equals(sector) && track.getStatus() == status) {
                    matches.add(track);
                }
            }
            assertThat(matches).isNotEmpty();
        });
        return matches.get(0);
    }

    /** The persisted lifecycle chain for a sector's track, in order, as {@code from->to} strings. */
    private List<String> lifecycleTransitions(String sector) {
        return jdbc.queryForList(
                "SELECT COALESCE(from_status,'') || '->' || to_status AS t FROM track_lifecycle_events"
                        + " WHERE track_id IN (SELECT track_id FROM tracks WHERE sector_id = ?)"
                        + " ORDER BY occurred_at, to_status", String.class, sector);
    }

    private void publish(DetectionRecord record) {
        try (KafkaProducer<String, byte[]> producer = producer()) {
            producer.send(new ProducerRecord<>(NORMALIZED, record.getSensorId(), record.toByteArray()));
            producer.flush();
        }
    }

    private KafkaProducer<String, byte[]> producer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(props);
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

    private String trackIdIn(String sector) {
        return string("SELECT track_id FROM tracks WHERE sector_id = ?", sector);
    }

    private long tracksInSector(String sector) {
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM tracks WHERE sector_id = ?", Long.class, sector);
        return value == null ? 0L : value;
    }

    private int number(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String string(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
