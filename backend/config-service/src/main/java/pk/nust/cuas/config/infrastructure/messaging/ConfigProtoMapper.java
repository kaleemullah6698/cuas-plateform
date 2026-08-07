package pk.nust.cuas.config.infrastructure.messaging;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.v1.ConfigNamespace;
import pk.nust.cuas.config.v1.ConfigUpdated;

/** Maps the domain {@link Configuration} to the {@link ConfigUpdated} contract message. */
@Component
public class ConfigProtoMapper {

    private static final String SCHEMA_VERSION = "1.0.0";

    public ConfigUpdated toProto(Configuration configuration) {
        return ConfigUpdated.newBuilder()
                .setNamespace(configuration.namespace())
                .setKind(kind(configuration.kind()))
                .setVersion(configuration.version())
                .setValueJson(configuration.deleted() ? "" : configuration.valueJson())
                .setDeleted(configuration.deleted())
                .setActorId(configuration.lastActorId().orElse(""))
                .setJustification(configuration.lastJustification().orElse(""))
                .setUpdatedAt(toTimestamp(configuration.updatedAt()))
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(CorrelationId.currentOrNew())
                .build();
    }

    static ConfigNamespace kind(ConfigNamespaceKind kind) {
        return switch (kind) {
            case SENSOR -> ConfigNamespace.CONFIG_NAMESPACE_SENSOR;
            case POLICY -> ConfigNamespace.CONFIG_NAMESPACE_POLICY;
            case AI -> ConfigNamespace.CONFIG_NAMESPACE_AI;
            case FEATURE_FLAG -> ConfigNamespace.CONFIG_NAMESPACE_FEATURE_FLAG;
            case RETENTION -> ConfigNamespace.CONFIG_NAMESPACE_RETENTION;
            case PROTECTED_ASSET -> ConfigNamespace.CONFIG_NAMESPACE_PROTECTED_ASSET;
            case GENERAL -> ConfigNamespace.CONFIG_NAMESPACE_GENERAL;
        };
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
