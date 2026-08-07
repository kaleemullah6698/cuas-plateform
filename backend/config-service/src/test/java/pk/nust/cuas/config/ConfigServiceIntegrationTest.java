package pk.nust.cuas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.config.application.ConfigNotFoundException;
import pk.nust.cuas.config.application.ConfigurationService;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.ConfigVersion;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.domain.port.ConfigEventPublisher;

/**
 * Configuration change, versioning, rollback and the change-log audit against a real PostgreSQL
 * (ARCHITECTURE.md §4.1.12). Against a real database because what matters is that the current state,
 * the version history and the change log commit together, the {@code uq_config_version} constraint
 * holds, and the Flyway migration validates against the entities. Kafka is mocked — the publisher is a
 * port whose adapter needs no broker to prove which change the service decides to announce.
 */
@SpringBootTest(properties = "grpc.server.enabled=false")
@ActiveProfiles("test")
@Testcontainers
class ConfigServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("configdb");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private ConfigurationService configurations;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ConfigEventPublisher publisher;

    @BeforeEach
    void freshState() {
        jdbc.execute("TRUNCATE configurations, config_versions, config_change_log RESTART IDENTITY");
    }

    @Test
    void aChangeOpensThenAdvancesTheVersionAndIsAudited() {
        configurations.update("policy", ConfigNamespaceKind.POLICY, "{\"a\":1}", "usr-1", "initial");
        Configuration v2 = configurations.update("policy", ConfigNamespaceKind.POLICY, "{\"a\":2}",
                "usr-2", "retune");

        assertThat(v2.version()).isEqualTo(2);
        assertThat(configurations.get("policy")).map(Configuration::version).contains(2L);
        assertThat(configurations.history("policy", 10)).extracting(ConfigVersion::version)
                .containsExactly(2L, 1L);
        // The change log records both changes with their justifications — the §4.1.12 audit trail.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM config_change_log WHERE namespace = 'policy'", Long.class))
                .isEqualTo(2L);
        verify(publisher, times(2)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aValueIsStoredCanonicalised() {
        Configuration config = configurations.update("policy", ConfigNamespaceKind.POLICY,
                "{\"b\":1,\"a\":2}", "usr-1", "initial");

        assertThat(config.valueJson()).isEqualTo("{\"a\":2,\"b\":1}");
    }

    @Test
    void aRollbackRestoresAnEarlierValueAsANewVersion() {
        configurations.update("policy", ConfigNamespaceKind.POLICY, "{\"a\":1}", "usr-1", "v1");
        configurations.update("policy", ConfigNamespaceKind.POLICY, "{\"a\":2}", "usr-1", "v2");

        Configuration rolled = configurations.rollback("policy", 1, "usr-3", "revert");

        assertThat(rolled.version()).isEqualTo(3);
        assertThat(rolled.valueJson()).isEqualTo("{\"a\":1}");
        assertThat(configurations.get("policy")).map(Configuration::valueJson).contains("{\"a\":1}");
    }

    @Test
    void aNamespacesKindIsFixedAtCreation() {
        configurations.update("policy", ConfigNamespaceKind.POLICY, "{}", "usr-1", "initial");

        assertThatThrownBy(() -> configurations.update("policy", ConfigNamespaceKind.SENSOR, "{}",
                "usr-1", "wrong kind"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void theSnapshotFiltersByKind() {
        configurations.update("policy", ConfigNamespaceKind.POLICY, "{}", "usr-1", "p");
        configurations.update("sensor:RF-01", ConfigNamespaceKind.SENSOR, "{}", "usr-1", "s");

        List<Configuration> policyOnly = configurations.snapshot(Set.of(ConfigNamespaceKind.POLICY));

        assertThat(policyOnly).extracting(Configuration::namespace).containsExactly("policy");
    }

    @Test
    void historyAndRollbackReportNotFoundForAnUnknownTarget() {
        assertThatThrownBy(() -> configurations.history("nope", 10))
                .isInstanceOf(ConfigNotFoundException.class);
        configurations.update("policy", ConfigNamespaceKind.POLICY, "{}", "usr-1", "v1");
        assertThatThrownBy(() -> configurations.rollback("policy", 99, "usr-1", "no such version"))
                .isInstanceOf(ConfigNotFoundException.class);
    }
}
