package pk.nust.cuas.notification.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.notification.api.dto.NotificationResponse;
import pk.nust.cuas.notification.api.dto.PreferenceDtos.PreferencesResponse;
import pk.nust.cuas.notification.api.dto.PreferenceDtos.UpdatePreferencesRequest;
import pk.nust.cuas.notification.application.NotificationDispatchService;
import pk.nust.cuas.notification.application.NotificationHistoryService;
import pk.nust.cuas.notification.application.PreferencesService;

/**
 * The notification self-service API (ARCHITECTURE.md section 4.1.9): a user's channel preferences and
 * quiet hours, their delivery history, and a test send.
 *
 * <p><strong>Every operation is scoped to the caller, taken from the JWT — never from the request.</strong>
 * A user reads and writes only their own preferences and history, so there is no path by which one
 * user reaches another's. This is why the endpoints carry no §7.4 permission: that matrix governs
 * shared resources (alerts, incidents, sensors), and a user's own notification settings are not one —
 * a VIEWER may set their own quiet hours. The gate is therefore authentication, not a permission, and
 * the self-scoping is the authorisation.
 */
@RestController
@RequestMapping("/notifications")
@Validated
@Tag(name = "Notifications", description = "A user's notification preferences, history and test send")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final PreferencesService preferences;
    private final NotificationHistoryService history;
    private final NotificationDispatchService dispatch;

    public NotificationController(PreferencesService preferences, NotificationHistoryService history,
                                  NotificationDispatchService dispatch) {
        this.preferences = preferences;
        this.history = history;
        this.dispatch = dispatch;
    }

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my notification preferences",
            description = "The caller's channels, quiet hours and minimum severity. Returns defaults "
                    + "(no quiet hours, minimum LOW, no channels) if none have been configured.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's preferences"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public PreferencesResponse getPreferences(@AuthenticationPrincipal Jwt caller) {
        return preferences.get(callerId(caller))
                .map(PreferencesResponse::from)
                .orElseGet(PreferencesResponse::defaults);
    }

    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Replace my notification preferences",
            description = "The body is the complete desired state: a channel absent from it is removed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The saved preferences"),
            @ApiResponse(responseCode = "400", description = "An invalid channel, address or quiet-hours pair"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public PreferencesResponse putPreferences(@Valid @RequestBody UpdatePreferencesRequest request,
                                              @AuthenticationPrincipal Jwt caller) {
        UUID userId = callerId(caller);
        log.debug("Updating notification preferences for {} [{}]", userId, CorrelationId.currentOrNew());
        preferences.save(request.toDomain(userId));
        return preferences.get(userId).map(PreferencesResponse::from).orElseGet(PreferencesResponse::defaults);
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my notification history",
            description = "The caller's notifications, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's notifications"),
            @ApiResponse(responseCode = "400", description = "An invalid limit"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public List<NotificationResponse> getHistory(
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
            @AuthenticationPrincipal Jwt caller) {
        return history.history(callerId(caller), limit).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PostMapping("/test")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send myself a test notification",
            description = "Delivers a test on the caller's preferred channel, bypassing quiet hours "
                    + "and the severity floor, so a user can verify their channel works.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The test notification and its outcome"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "409", description = "No enabled channel is configured")
    })
    public NotificationResponse sendTest(@AuthenticationPrincipal Jwt caller) {
        UUID userId = callerId(caller);
        String correlationId = CorrelationId.currentOrNew();
        log.info("Sending a test notification to {} [{}]", userId, correlationId);
        return dispatch.sendTest(userId, correlationId)
                .map(NotificationResponse::from)
                .orElseThrow(NoEnabledChannelException::new);
    }

    private static UUID callerId(Jwt caller) {
        return UUID.fromString(caller.getSubject());
    }
}
