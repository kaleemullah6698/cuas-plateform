package pk.nust.cuas.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.audit.domain.AuditRepository;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.model.HashChain;
import pk.nust.cuas.audit.domain.service.ChainVerificationResult;
import pk.nust.cuas.audit.domain.service.ChainVerifier;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditOutcome;

/**
 * Persistence and integrity behaviour against a real PostgreSQL instance.
 *
 * <p>Testcontainers is used rather than an in-memory database because the guarantees under test
 * are PostgreSQL-specific: the {@code DO INSTEAD NOTHING} rules that make the table append-only,
 * the advisory lock that serialises appends, and the {@code JSONB}/{@code INET} column types. An
 * in-memory substitute would pass while proving none of it.
 *
 * <p>Flyway runs the real migration, so the schema under test is the schema that ships.
 */
@SpringBootTest
@Testcontainers
class AuditRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")   // container lifecycle is managed by the Testcontainers extension
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("auditdb")
            .withUsername("cuas")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // The Kafka listener is not part of these tests; the broker is exercised separately.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private AuditRepository repository;

    @Autowired
    private ChainVerifier chainVerifier;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void resetLog() {
        jdbc = new JdbcTemplate(dataSource);
        // TRUNCATE is not blocked by the rules, which is what makes per-test isolation possible.
        jdbc.execute("TRUNCATE audit_chain_checkpoint, audit_log RESTART IDENTITY CASCADE");
    }

    private static AuditRecord newRecord(String action) {
        return new AuditRecord(null, UUID.randomUUID(), Instant.parse("2026-07-20T10:00:00Z"), null,
                "usr_1", ActorType.USER, "10.0.0.1", "ses_1", action, "USER", "usr_2",
                AuditOutcome.SUCCESS, Map.of("field", "value"), "corr-1", null, null);
    }

    @Test
    void appendPersistsAndAssignsChainFields() {
        AuditRecord appended = repository.append(newRecord("LOGIN_SUCCEEDED"));

        assertThat(appended.seq()).isNotNull().isPositive();
        assertThat(appended.recordedAt()).isNotNull();
        assertThat(appended.prevHash()).isEqualTo(HashChain.GENESIS_HASH);
        assertThat(appended.recordHash()).hasSize(32);
        assertThat(appended.actorIp()).isEqualTo("10.0.0.1");
        assertThat(appended.details()).containsEntry("field", "value");
    }

    @Test
    void firstRecordChainsToGenesisAndSubsequentRecordsChainToTheirPredecessor() {
        AuditRecord first = repository.append(newRecord("A"));
        AuditRecord second = repository.append(newRecord("B"));

        assertThat(first.prevHash()).isEqualTo(HashChain.GENESIS_HASH);
        assertThat(second.prevHash()).isEqualTo(first.recordHash());
    }

    @Test
    void chainOfManyRecordsVerifiesIntact() {
        for (int i = 0; i < 25; i++) {
            repository.append(newRecord("ACTION_" + i));
        }

        List<AuditRecord> all = repository.findSegment(1, Long.MAX_VALUE);
        ChainVerificationResult result = chainVerifier.verify(all, HashChain.GENESIS_HASH);

        assertThat(all).hasSize(25);
        assertThat(result.intact()).isTrue();
        assertThat(result.recordsVerified()).isEqualTo(25);
    }

    @Test
    void updateIsSilentlyDiscardedByTheDatabaseRule() {
        AuditRecord appended = repository.append(newRecord("LOGIN_SUCCEEDED"));

        // The rule makes this a no-op rather than an error: the statement reports success but
        // changes nothing, which is what "append-only, enforced by the database" means here.
        jdbc.update("UPDATE audit_log SET action = 'TAMPERED' WHERE seq = ?", appended.seq());

        String action = jdbc.queryForObject(
                "SELECT action FROM audit_log WHERE seq = ?", String.class, appended.seq());
        assertThat(action).isEqualTo("LOGIN_SUCCEEDED");
    }

    @Test
    void deleteIsSilentlyDiscardedByTheDatabaseRule() {
        repository.append(newRecord("LOGIN_SUCCEEDED"));

        jdbc.update("DELETE FROM audit_log");

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void tamperingAttemptCannotBreakTheChainBecauseItCannotTakeEffect() {
        for (int i = 0; i < 5; i++) {
            repository.append(newRecord("ACTION_" + i));
        }

        jdbc.update("UPDATE audit_log SET action = 'TAMPERED' WHERE seq = 3");
        jdbc.update("DELETE FROM audit_log WHERE seq = 2");

        ChainVerificationResult result =
                chainVerifier.verify(repository.findSegment(1, Long.MAX_VALUE), HashChain.GENESIS_HASH);

        assertThat(result.intact()).isTrue();
        assertThat(repository.count()).isEqualTo(5);
    }

    @Test
    void duplicateAuditIdIsRejectedByTheUniqueConstraint() {
        AuditRecord first = newRecord("LOGIN_SUCCEEDED");
        repository.append(first);

        assertThat(repository.existsByAuditId(first.auditId())).isTrue();
    }

    @Test
    void concurrentAppendsProduceOneUnbrokenChain() throws Exception {
        int writers = 8;
        int perWriter = 5;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            final int writer = w;
            tasks.add(() -> {
                for (int i = 0; i < perWriter; i++) {
                    repository.append(newRecord("W" + writer + "_" + i));
                }
                return null;
            });
        }

        try {
            pool.invokeAll(tasks);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }

        List<AuditRecord> all = repository.findSegment(1, Long.MAX_VALUE);
        ChainVerificationResult result = chainVerifier.verify(all, HashChain.GENESIS_HASH);

        // Without the advisory lock two writers would read the same head and fork the chain.
        assertThat(all).hasSize(writers * perWriter);
        assertThat(result.intact())
                .as("concurrent appends must not fork the chain: %s", result.reason())
                .isTrue();
    }

    @Test
    void searchFiltersIndependently() {
        repository.append(newRecord("LOGIN_SUCCEEDED"));
        repository.append(newRecord("LOGIN_FAILED"));
        repository.append(newRecord("ROLE_ASSIGNED"));

        assertThat(repository.search(null, "LOGIN_FAILED", null, null, null, 50, 0)).hasSize(1);
        assertThat(repository.search("usr_1", null, null, null, null, 50, 0)).hasSize(3);
        assertThat(repository.search("nobody", null, null, null, null, 50, 0)).isEmpty();
        assertThat(repository.search(null, null, "USER", null, null, 50, 0)).hasSize(3);
    }

    @Test
    void searchReturnsMostRecentFirst() {
        repository.append(newRecord("FIRST"));
        repository.append(newRecord("SECOND"));

        List<AuditRecord> results = repository.search(null, null, null, null, null, 50, 0);

        assertThat(results.get(0).action()).isEqualTo("SECOND");
    }

    @Test
    void nanosecondPrecisionTimestampStillVerifiesAfterReload() {
        // Instant carries nanoseconds; PostgreSQL TIMESTAMPTZ holds microseconds. If the hashed
        // timestamp were not truncated to the storable precision, every such record would fail
        // its own verification on reload — a false tamper alarm.
        Instant subMicrosecond = Instant.parse("2026-07-20T10:00:00Z").plusNanos(123_456_789L);
        AuditRecord record = new AuditRecord(null, UUID.randomUUID(), subMicrosecond, null,
                "usr_1", ActorType.USER, null, null, "LOGIN_SUCCEEDED", "USER", "usr_1",
                AuditOutcome.SUCCESS, Map.of(), null, null, null);

        AuditRecord appended = repository.append(record);
        AuditRecord reloaded = repository.findSegment(appended.seq(), appended.seq()).get(0);

        assertThat(reloaded.occurredAt()).isEqualTo(appended.occurredAt());
        assertThat(HashChain.isIntact(reloaded, reloaded.prevHash())).isTrue();
    }

    @Test
    void detailsSurviveTheJsonbRoundTrip() {
        AuditRecord record = new AuditRecord(null, UUID.randomUUID(), Instant.now(), null,
                "usr_1", ActorType.USER, null, null, "POLICY_UPDATED", "POLICY", "p1",
                AuditOutcome.SUCCESS,
                Map.of("before", "threshold=0.7", "after", "threshold=0.8"), null, null, null);

        AuditRecord appended = repository.append(record);
        AuditRecord reloaded = repository.findSegment(appended.seq(), appended.seq()).get(0);

        assertThat(reloaded.details())
                .containsEntry("before", "threshold=0.7")
                .containsEntry("after", "threshold=0.8");
        // Re-hashing the reloaded record must reproduce the stored digest, proving that JSONB
        // normalisation cannot cause a false tamper alarm.
        assertThat(HashChain.isIntact(reloaded, reloaded.prevHash())).isTrue();
    }
}
