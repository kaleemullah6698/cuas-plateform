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
 * Cross-sensor duplicate elimination against real PostgreSQL and Kafka (ARCHITECTURE.md section
 * 4.1.5): two sensors independently open a track on one object, the pair holds together across the
 * required sweeps, and the younger is absorbed into the older.
 *
 * <p>The association distance gate is shrunk far below the merge gate. That is what makes the
 * scenario reachable at all: with the production gate, two detections a few metres apart would
 * associate into a single track and there would be nothing to merge. Two sensors reporting the same
 * object at slightly different positions — which is what actually happens, since each has its own
 * bias — is exactly the case the merge exists for, and a tight association gate reproduces it.
 *
 * <p>The timeout sweep is pushed far out so tracks stay {@code CONFIRMED} for the duration; coasting
 * would make them ineligible and the test would be measuring the wrong thing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "correlation.association.distance-gate-m=5",
        "correlation.merge.gate-m=500",
        "correlation.merge.required-consecutive-sweeps=3",
        "correlation.merge.check-interval=PT1S",
        "correlation.lifecycle.coast-timeout=PT10M",
        "correlation.lifecycle.lost-timeout=PT20M",
        "correlation.lifecycle.check-interval=PT10M"})
@Testcontainers
class TrackMergeIntegrationTest {

    private static final String NORMALIZED = "sensor.detection.normalized.v1";
    private static final double EARTH_RADIUS_M = 6_371_000.0d;

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
    void twoSensorsTrackingOneObjectMergeIntoTheOlderTrack() {
        double lat = 40.5;
        double lon = 50.5;
        String sector = "SECTOR_40_50";

        try (KafkaConsumer<String, byte[]> merges = subscribe("track.merged.v1")) {
            // Two sensors, 80 m apart — outside the 5 m association gate, inside the 500 m merge gate.
            confirmTrackFrom("radar-1", lat, lon);
            confirmTrackFrom("rf-1", lat, eastOf(lon, lat, 80));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(countIn(sector, "CONFIRMED"))
                            .as("two separate confirmed tracks before any merge")
                            .isEqualTo(2));

            // The sweep needs three consecutive passes inside the gate before it will act.
            await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                    assertThat(countIn(sector, "MERGED")).isEqualTo(1));

            Track merged = awaitMerged(merges, sector);
            assertThat(merged.getStatus()).isEqualTo(TrackStatus.TRACK_STATUS_MERGED);
            assertThat(merged.getMergedIntoTrackId()).isNotBlank();

            // The older track survives; UUIDv7 ordering makes "older" the lower id.
            String survivor = merged.getMergedIntoTrackId();
            String absorbed = merged.getTrackId();
            assertThat(survivor.compareTo(absorbed))
                    .as("the survivor is the older (lower UUIDv7) of the pair")
                    .isNegative();
            assertThat(statusOf(survivor)).isEqualTo("CONFIRMED");
            assertThat(statusOf(absorbed)).isEqualTo("MERGED");
            assertThat(mergedIntoOf(absorbed)).isEqualTo(survivor);

            // The merge is a recorded lifecycle transition, not a silent status edit.
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM track_lifecycle_events WHERE track_id = ?::uuid"
                            + " AND to_status = 'MERGED'", Long.class, absorbed)).isEqualTo(1L);

            // The pair's evidence is discarded once it has been acted on.
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM track_merge_candidates WHERE lower_track_id = ?::uuid"
                            + " OR higher_track_id = ?::uuid", Long.class, absorbed, absorbed))
                    .isZero();
        }
    }

    @Test
    void twoTracksFedByTheSameSensorAreNeverMerged() {
        // Two drones in formation, both seen by one sensor. Merging them would make one of two real
        // threats vanish, so a shared sensor disqualifies the pair however close they fly.
        double lat = 41.5;
        double lon = 51.5;
        String sector = "SECTOR_41_51";

        confirmTrackFrom("shared-radar", lat, lon);
        confirmTrackFrom("shared-radar", lat, eastOf(lon, lat, 80));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(countIn(sector, "CONFIRMED")).isEqualTo(2));

        // Well past the three sweeps a cross-sensor pair would have needed.
        await().during(Duration.ofSeconds(8)).atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(countIn(sector, "MERGED")).isZero());
        assertThat(countIn(sector, "CONFIRMED")).isEqualTo(2);
    }

    /** Publishes enough detections from one sensor at one position to confirm a track. */
    private void confirmTrackFrom(String sensorId, double lat, double lon) {
        Instant now = Instant.now();
        for (int seq = 1; seq <= 3; seq++) {
            publish(Detections.normalized(sensorId + '-' + seq + '-' + UUID.randomUUID(),
                    sensorId, seq, lat, lon, now.plusMillis(seq * 10L)));
        }
    }

    private static double eastOf(double longitude, double latitude, double metres) {
        return longitude + Math.toDegrees(
                metres / (EARTH_RADIUS_M * Math.cos(Math.toRadians(latitude))));
    }

    private Track awaitMerged(KafkaConsumer<String, byte[]> consumer, String sector) {
        List<Track> matches = new ArrayList<>();
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> record : records) {
                Track track = Track.parseFrom(record.value());
                if (track.getSectorId().equals(sector)) {
                    matches.add(track);
                }
            }
            assertThat(matches).isNotEmpty();
        });
        return matches.get(0);
    }

    private long countIn(String sector, String status) {
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM tracks WHERE sector_id = ? AND status = ?", Long.class, sector, status);
        return value == null ? 0L : value;
    }

    private String statusOf(String trackId) {
        return jdbc.queryForObject("SELECT status FROM tracks WHERE track_id = ?::uuid",
                String.class, trackId);
    }

    private String mergedIntoOf(String trackId) {
        return jdbc.queryForObject(
                "SELECT merged_into_track_id::text FROM tracks WHERE track_id = ?::uuid",
                String.class, trackId);
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
}
