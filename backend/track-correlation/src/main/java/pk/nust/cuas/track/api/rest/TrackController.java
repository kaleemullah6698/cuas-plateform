package pk.nust.cuas.track.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.track.api.dto.TrackResponse;
import pk.nust.cuas.track.api.dto.TrackStateResponse;
import pk.nust.cuas.track.application.TrackQueryService;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.TrackStatus;

/**
 * Read API for tracks (ARCHITECTURE.md section 4.1.5: {@code GET /tracks}, {@code /tracks/{id}},
 * {@code /tracks/{id}/history}).
 *
 * <p>Every operation is guarded by {@code track:read} — the section 7.4 permission every role holds
 * — enforced here as the authoritative layer (section 7.4). There are no mutating endpoints: a
 * track changes only by consuming detections, never over HTTP.
 */
@RestController
@RequestMapping("/api/v1/tracks")
@Validated
@Tag(name = "Tracks", description = "Read access to correlated tracks")
public class TrackController {

    private final TrackQueryService tracks;

    public TrackController(TrackQueryService tracks) {
        this.tracks = tracks;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('track:read')")
    @Operation(summary = "List tracks", description = "Lists tracks, newest last-seen first, optionally"
            + " filtered by sector and status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching tracks"),
            @ApiResponse(responseCode = "400", description = "An invalid status or limit"),
            @ApiResponse(responseCode = "403", description = "Missing the track:read permission")
    })
    public List<TrackResponse> list(
            @RequestParam Optional<String> sector,
            @RequestParam Optional<TrackStatus> status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return tracks.list(sector.map(SectorId::new), status, limit).stream()
                .map(TrackResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('track:read')")
    @Operation(summary = "Get a track by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The track"),
            @ApiResponse(responseCode = "404", description = "No such track"),
            @ApiResponse(responseCode = "403", description = "Missing the track:read permission")
    })
    public TrackResponse get(@PathVariable UUID id) {
        return TrackResponse.from(tracks.get(id));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('track:read')")
    @Operation(summary = "Get a track's observed-state history, newest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The track's states"),
            @ApiResponse(responseCode = "404", description = "No such track"),
            @ApiResponse(responseCode = "403", description = "Missing the track:read permission")
    })
    public List<TrackStateResponse> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return tracks.history(id, limit).stream()
                .map(TrackStateResponse::from)
                .toList();
    }
}
