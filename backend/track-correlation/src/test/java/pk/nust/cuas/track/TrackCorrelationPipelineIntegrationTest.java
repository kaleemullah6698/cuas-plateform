package pk.nust.cuas.track;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.track.support.Detections;

/**
 * The assembled service against real PostgreSQL and Kafka: a normalised detection consumed off the
 * topic becomes a track that is persisted and published, nearby detections build one confirmed
 * track, a distant detection starts another, and the read API is guarded by {@code track:read}.
 *
 * <p>Each test uses its own position — and therefore its own sector — and asserts only on that
 * sector. That is what keeps the tests isolated: the service's consumer processes detections
 * asynchronously and its data outlives a single test, so a shared position would let one test's
 * detections associate into another's tracks. A unique sector per test avoids that entirely, so no
 * cross-test truncation is needed or wanted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
// This test uses fixed past timestamps and asserts on the tentative/confirmed states, so the timeout
// sweep must not run against its tracks; a very long interval keeps it out of the way. The coasting
// and lost transitions are exercised deterministically in TrackLifecycleIntegrationTest.
@TestPropertySource(properties = "correlation.lifecycle.check-interval=PT1H")
@Testcontainers
class TrackCorrelationPipelineIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-08-04T12:00:00Z");
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

    @Autowired
    private MockMvc mockMvc;

    private JdbcTemplate jdbc;

    @BeforeEach
    void jdbc() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void aDetectionBecomesATentativeTrackPublishedAndPersisted() throws Exception {
        // Unique cell: SECTOR_10_20.
        double lat = 10.5;
        double lon = 20.5;
        String sector = "SECTOR_10_20";
        String detectionId = "d1-" + UUID.randomUUID();

        try (KafkaConsumer<String, byte[]> created = subscribe("track.created.v1")) {
            publish(Detections.normalized(detectionId, "rf-1", 1L, lat, lon, BASE));

            pk.nust.cuas.track.v1.Track emitted = awaitTrack(created, sector);
            assertThat(emitted.getStatus()).isEqualTo(pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_TENTATIVE);
            assertThat(emitted.getPosition().getLatitude()).isEqualTo(lat);
            assertThat(emitted.getDetectionCount()).isEqualTo(1);
            assertThat(emitted.getLastDetectionId()).isEqualTo(detectionId);
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(tracksInSector(sector)).isEqualTo(1);
            assertThat(string("SELECT status FROM tracks WHERE sector_id = ?", sector)).isEqualTo("TENTATIVE");
        });
    }

    @Test
    void threeNearbyDetectionsFormOneConfirmedTrack() {
        double lat = 11.5;
        double lon = 21.5;
        String sector = "SECTOR_11_21";
        for (int seq = 1; seq <= 3; seq++) {
            publish(Detections.normalized("dc-" + seq + '-' + UUID.randomUUID(), "rf-confirm", seq,
                    lat, lon, BASE.plusSeconds(seq)));
        }

        // Aggregate so the query returns 0 (not an empty result that would throw) before the track exists.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(number("SELECT COALESCE(MAX(detection_count),0) FROM tracks WHERE sector_id = ?", sector))
                        .isEqualTo(3));

        assertThat(tracksInSector(sector)).isEqualTo(1);
        assertThat(string("SELECT status FROM tracks WHERE sector_id = ?", sector)).isEqualTo("CONFIRMED");
        assertThat(count("SELECT count(*) FROM track_states WHERE track_id IN "
                + "(SELECT track_id FROM tracks WHERE sector_id = ?)", sector)).isEqualTo(3);
        // Two lifecycle events: creation, then confirmation.
        assertThat(count("SELECT count(*) FROM track_lifecycle_events WHERE track_id IN "
                + "(SELECT track_id FROM tracks WHERE sector_id = ?)", sector)).isEqualTo(2);
    }

    @Test
    void aDistantDetectionStartsASeparateTrack() {
        publish(Detections.normalized("near-" + UUID.randomUUID(), "rf-a", 1L, 12.5, 22.5, BASE));
        // A different grid cell entirely: a new track, never associated to the first.
        publish(Detections.normalized("far-" + UUID.randomUUID(), "rf-b", 1L, 13.5, 23.5, BASE.plusSeconds(1)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(tracksInSector("SECTOR_12_22")).isEqualTo(1);
            assertThat(tracksInSector("SECTOR_13_23")).isEqualTo(1);
        });
    }

    @Test
    void theReadApiIsGuardedByTrackReadPermission() throws Exception {
        String sector = "SECTOR_14_24";
        publish(Detections.normalized("dr-" + UUID.randomUUID(), "rf-read", 1L, 14.5, 24.5, BASE));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(tracksInSector(sector)).isEqualTo(1));
        String trackId = string("SELECT track_id FROM tracks WHERE sector_id = ?", sector);

        // With track:read → 200 and the track is returned.
        mockMvc.perform(get("/tracks/{id}", trackId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("track:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackId").value(trackId))
                .andExpect(jsonPath("$.status").value("TENTATIVE"));

        // Unauthenticated → 401.
        mockMvc.perform(get("/tracks/{id}", trackId)).andExpect(status().isUnauthorized());

        // Authenticated but without track:read → 403.
        mockMvc.perform(get("/tracks/{id}", trackId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("track:classify"))))
                .andExpect(status().isForbidden());

        // Unknown id → 404.
        mockMvc.perform(get("/tracks/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("track:read"))))
                .andExpect(status().isNotFound());

        // The list endpoint, filtered to this test's sector, returns the track.
        mockMvc.perform(get("/tracks").param("sector", sector)
                        .with(jwt().authorities(new SimpleGrantedAuthority("track:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackId").value(trackId));
    }

    private void publish(DetectionRecord record) {
        try (KafkaProducer<String, byte[]> producer = producer()) {
            producer.send(new ProducerRecord<>(NORMALIZED, record.getSensorId(), record.toByteArray()));
            producer.flush();
        }
    }

    private pk.nust.cuas.track.v1.Track awaitTrack(KafkaConsumer<String, byte[]> consumer, String sector) {
        List<pk.nust.cuas.track.v1.Track> matches = new ArrayList<>();
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> record : records) {
                pk.nust.cuas.track.v1.Track track = pk.nust.cuas.track.v1.Track.parseFrom(record.value());
                if (track.getSectorId().equals(sector)) {
                    matches.add(track);
                }
            }
            assertThat(matches).isNotEmpty();
        });
        return matches.get(0);
    }

    private long tracksInSector(String sector) {
        return count("SELECT count(*) FROM tracks WHERE sector_id = ?", sector);
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

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
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
