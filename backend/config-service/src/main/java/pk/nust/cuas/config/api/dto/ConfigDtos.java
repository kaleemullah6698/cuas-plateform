package pk.nust.cuas.config.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.ConfigVersion;
import pk.nust.cuas.config.domain.model.Configuration;

/** Request and response DTOs for the configuration API ({@code /config/{namespace}}). */
public final class ConfigDtos {

    private ConfigDtos() {
    }

    /**
     * Sets a namespace's value.
     *
     * @param kind          the namespace family — fixed at creation, and it decides which permission
     *                      may change the namespace
     * @param value         the new value, a JSON object
     * @param justification why — required, because a configuration change is a critical action (§7.4)
     */
    public record UpdateConfigRequest(@NotNull ConfigNamespaceKind kind, @NotNull JsonNode value,
                                      @NotBlank String justification) {
    }

    /**
     * Restores a namespace to an earlier version.
     *
     * @param toVersion     the version whose value to restore
     * @param justification why
     */
    public record RollbackRequest(@Min(1) long toVersion, @NotBlank String justification) {
    }

    /**
     * A namespace's current state.
     *
     * @param namespace        the namespace
     * @param kind             its family
     * @param version          its current version
     * @param value            its current value
     * @param deleted          whether it is currently deleted
     * @param lastActorId      who last changed it
     * @param lastJustification why they last changed it
     * @param updatedAt        when it was last changed
     */
    public record ConfigResponse(String namespace, ConfigNamespaceKind kind, long version, JsonNode value,
                                 boolean deleted, String lastActorId, String lastJustification,
                                 Instant updatedAt) {

        public static ConfigResponse of(Configuration c, JsonNode value) {
            return new ConfigResponse(c.namespace(), c.kind(), c.version(), value, c.deleted(),
                    c.lastActorId().orElse(null), c.lastJustification().orElse(null), c.updatedAt());
        }
    }

    /**
     * One entry in a namespace's history.
     *
     * @param namespace     the namespace
     * @param version       the version
     * @param value         the value at that version
     * @param deleted       whether it was deleted at that version
     * @param actorId       who made the change
     * @param justification why
     * @param createdAt     when
     */
    public record ConfigVersionResponse(String namespace, long version, JsonNode value, boolean deleted,
                                        String actorId, String justification, Instant createdAt) {

        public static ConfigVersionResponse of(ConfigVersion v, JsonNode value) {
            return new ConfigVersionResponse(v.namespace(), v.version(), value, v.deleted(),
                    v.actorId().orElse(null), v.justification().orElse(null), v.createdAt());
        }
    }
}
