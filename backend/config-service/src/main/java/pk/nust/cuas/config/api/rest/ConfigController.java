package pk.nust.cuas.config.api.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.config.api.dto.ConfigDtos.ConfigResponse;
import pk.nust.cuas.config.api.dto.ConfigDtos.ConfigVersionResponse;
import pk.nust.cuas.config.api.dto.ConfigDtos.RollbackRequest;
import pk.nust.cuas.config.api.dto.ConfigDtos.UpdateConfigRequest;
import pk.nust.cuas.config.application.ConfigNotFoundException;
import pk.nust.cuas.config.application.ConfigurationService;
import pk.nust.cuas.config.domain.model.Configuration;

/**
 * The configuration API (ARCHITECTURE.md §4.1.12).
 *
 * <p><b>Writes are gated per namespace kind by {@link ConfigAuthorizer}</b>, using only §7.4
 * permissions and enforced server-side; the actor is taken from the token, never the body, so a
 * change is always recorded against the person who made it. <b>Reads are gated on {@code policy:read}</b>
 * — the matrix's operational-config read permission, held by every operational role — because §7.4
 * has no generic {@code config:read} and §0 forbids inventing one. This is the config-service analogue
 * of notification-service's self-service decision, flagged rather than papered over.
 */
@RestController
@RequestMapping("/api/v1/config")
@Validated
@Tag(name = "Configuration", description = "Runtime configuration namespaces, their history and rollback")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final ConfigurationService configurations;
    private final ConfigAuthorizer authorizer;
    private final ObjectMapper objectMapper;

    public ConfigController(ConfigurationService configurations, ConfigAuthorizer authorizer,
                            ObjectMapper objectMapper) {
        this.configurations = configurations;
        this.authorizer = authorizer;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{namespace}")
    @PreAuthorize("hasAuthority('policy:read')")
    @Operation(summary = "Get a configuration namespace's current value")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The current value"),
            @ApiResponse(responseCode = "403", description = "Missing the policy:read permission"),
            @ApiResponse(responseCode = "404", description = "No such namespace")
    })
    public ConfigResponse get(@PathVariable String namespace) {
        Configuration configuration = configurations.get(namespace).orElseThrow(
                () -> new ConfigNotFoundException("no configuration namespace '" + namespace + "'"));
        return ConfigResponse.of(configuration, parse(configuration.valueJson()));
    }

    @PutMapping("/{namespace}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set a configuration namespace's value",
            description = "Opens the namespace if new, advances its version if it exists. Requires the "
                    + "kind's §7.4 permission and a justification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The new current value"),
            @ApiResponse(responseCode = "400", description = "An invalid value, or a kind that "
                    + "contradicts the namespace's existing kind"),
            @ApiResponse(responseCode = "403", description = "Missing the kind's write permission")
    })
    public ConfigResponse put(@PathVariable String namespace,
                              @Valid @RequestBody UpdateConfigRequest request,
                              @AuthenticationPrincipal Jwt caller) {
        authorizer.requireWrite(request.kind(), caller);
        log.info("Setting configuration '{}' ({}) [{}]", namespace, request.kind(),
                CorrelationId.currentOrNew());
        Configuration updated = configurations.update(namespace, request.kind(),
                request.value().toString(), caller.getSubject(), request.justification());
        return ConfigResponse.of(updated, parse(updated.valueJson()));
    }

    @GetMapping("/{namespace}/history")
    @PreAuthorize("hasAuthority('policy:read')")
    @Operation(summary = "Get a configuration namespace's version history, newest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The history"),
            @ApiResponse(responseCode = "400", description = "An invalid limit"),
            @ApiResponse(responseCode = "403", description = "Missing the policy:read permission"),
            @ApiResponse(responseCode = "404", description = "No such namespace")
    })
    public List<ConfigVersionResponse> history(@PathVariable String namespace,
                                               @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return configurations.history(namespace, limit).stream()
                .map(version -> ConfigVersionResponse.of(version, parse(version.valueJson())))
                .toList();
    }

    @PostMapping("/{namespace}/rollback")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Roll a namespace back to an earlier version",
            description = "Restores the value of an earlier version as a new, higher version. Requires "
                    + "the namespace kind's write permission and a justification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The rolled-back current value"),
            @ApiResponse(responseCode = "400", description = "An invalid version"),
            @ApiResponse(responseCode = "403", description = "Missing the kind's write permission"),
            @ApiResponse(responseCode = "404", description = "No such namespace or version")
    })
    public ConfigResponse rollback(@PathVariable String namespace,
                                   @Valid @RequestBody RollbackRequest request,
                                   @AuthenticationPrincipal Jwt caller) {
        Configuration current = configurations.get(namespace).orElseThrow(
                () -> new ConfigNotFoundException("no configuration namespace '" + namespace + "'"));
        authorizer.requireWrite(current.kind(), caller);
        log.info("Rolling configuration '{}' back to version {} [{}]", namespace, request.toVersion(),
                CorrelationId.currentOrNew());
        Configuration rolled = configurations.rollback(namespace, request.toVersion(),
                caller.getSubject(), request.justification());
        return ConfigResponse.of(rolled, parse(rolled.valueJson()));
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            // The stored value is canonicalised JSON this service wrote; a parse failure is a bug, not
            // a client error.
            throw new IllegalStateException("stored configuration value is not valid JSON", ex);
        }
    }
}
