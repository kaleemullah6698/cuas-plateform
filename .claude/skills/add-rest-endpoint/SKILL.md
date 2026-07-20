---
name: add-rest-endpoint
description: Add a REST or gRPC endpoint to a backend service with the mandatory validation, exception handling, logging, DTOs, OpenAPI documentation, and RBAC authorisation. Use when creating a controller, API route, HTTP handler, or any new API surface.
---

# Adding an endpoint

`docs/ARCHITECTURE.md` §4.1 lists each service's permitted API surface. **If the endpoint is not there, stop and ask** — adding one is an architecture change.

## The six requirements

An endpoint missing any of these is incomplete. Not a preference — a definition.

| # | Requirement |
|---|---|
| 1 | Validation — Jakarta Bean Validation on the request DTO, `@Valid` at the parameter |
| 2 | Exception handling — via `@RestControllerAdvice`, never a raw stack trace |
| 3 | Logging — entry at DEBUG, failure at WARN/ERROR, with the correlation ID |
| 4 | DTOs — distinct request/response records; never domain models |
| 5 | OpenAPI — `@Operation` and `@ApiResponse` for every status returned |
| 6 | Authorisation — `@PreAuthorize` with the permission from the §7.4 RBAC matrix |

## Worked example

```java
// api/dto/CreateIncidentRequest.java — record, validated at the boundary
public record CreateIncidentRequest(
    @NotBlank @Size(max = 64)  String trackId,
    @NotNull                    IncidentSeverity severity,
    @Size(max = 2000)           String description
) {}
```

```java
// api/rest/IncidentController.java
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final CreateIncidentUseCase createIncident;   // constructor injection, no @Autowired
    private final IncidentDtoMapper mapper;

    public IncidentController(CreateIncidentUseCase createIncident, IncidentDtoMapper mapper) {
        this.createIncident = createIncident;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('incident:create')")          // §7.4 — server-side, authoritative
    @Operation(summary = "Declare an incident from a track")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Incident created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "403", description = "Caller lacks incident:create"),
        @ApiResponse(responseCode = "404", description = "Track not found")
    })
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        log.debug("Creating incident for track {}", request.trackId());

        Incident incident = createIncident.execute(mapper.toCommand(request));

        return ResponseEntity
            .created(URI.create("/api/v1/incidents/" + incident.id().value()))
            .body(mapper.toResponse(incident));
    }
}
```

Note what the controller does **not** do: no business logic, no repository access, no domain object returned. It translates, delegates, and maps back.

## Error responses — RFC 7807

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail(firstFieldError(ex));
        problem.setProperty("correlationId", CorrelationId.current());
        return problem;
    }
}
```

Never leak internal detail — no stack traces, no SQL, no class names in a 500 body.

## Rules

- **Base path** `/api/v1/<resource>`. Major version in the path; a breaking change mints `/api/v2/` and both run in parallel.
- **Plural nouns**, no verbs. `POST /incidents`, not `/createIncident`.
- **Status codes**: 201 with `Location` on creation, 202 for accepted async work, 403 when authenticated but not permitted, 429 with `Retry-After` when rate limited.
- **Pagination** on every collection: `?page=0&size=50&sort=threatScore,desc`.
- **Rate limits** per §7.8. Security-sensitive endpoints (`/auth/login`, `/audit/export`) have stricter buckets and audit their rejections.
- **Audit every mutation** (§7.5). If the audit write fails, the operation fails closed.

## gRPC

Same discipline. Service definitions come from `contracts/proto/` — never hand-write a stub. Implementations live in `api/grpc/`. Map domain exceptions to proper `Status` codes; never let one escape as `UNKNOWN`.

## Checklist

- [ ] Endpoint appears in the service's §4.1 API list
- [ ] All six requirements present
- [ ] Request and response are records, not domain models
- [ ] Permission string matches the §7.4 matrix exactly
- [ ] Errors are RFC 7807 with a correlation ID
- [ ] Mutation is audited
- [ ] `@WebMvcTest` covers success, validation failure, and forbidden
