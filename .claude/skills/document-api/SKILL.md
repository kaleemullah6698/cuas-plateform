---
name: document-api
description: Document APIs and keep project documentation current — OpenAPI/Swagger annotations, README updates, architecture-doc amendments, concise code comments. Use when adding an endpoint, changing a contract, or when documentation has drifted from the code.
---

# Documenting APIs and the project

`docs/ARCHITECTURE.md` is the single source of truth. Documentation exists to keep it, and the code, mutually honest.

## OpenAPI — on every endpoint

Not a separate task; part of the endpoint (see the `add-rest-endpoint` skill). Springdoc generates the spec from annotations, so the annotations must be complete:

```java
@Operation(summary = "Declare an incident from a track",
           description = "Creates an incident owned by the caller. Requires incident:create.")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Created",
                 content = @Content(schema = @Schema(implementation = IncidentResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed"),
    @ApiResponse(responseCode = "403", description = "Caller lacks incident:create"),
    @ApiResponse(responseCode = "404", description = "Track not found")
})
```

- Document **every** status the endpoint returns, including errors.
- Every DTO field annotated with `@Schema(description=…)`; mark required fields.
- Each service exposes `/v3/api-docs` and `/swagger-ui.html`; the gateway aggregates them.
- Error responses are RFC 7807 — say so in the schema.

## Keeping ARCHITECTURE.md honest

The document leads; the code follows. When a change affects design:

- Amend the document **in the same commit** as the code. A design change and its documentation are one unit of work.
- If reality has diverged from the document, the document is treated as authoritative — reconcile deliberately, don't silently let code win. Code that contradicts the architecture is a second, undocumented source of truth.
- Never rename a service, topic, or schema in code without amending §4/§5 first, and never without approval (see CLAUDE.md §0).

Sections §9–13 of `docs/ARCHITECTURE.md` are currently unwritten (roadmap, API examples, repo strategy, performance targets, final recommendation). If work touches those areas, that's the trigger to write the section, not to invent an answer inline.

## READMEs

- Root `README.md`: what the system is, the layout, how to start, current status. Update the status when a milestone lands.
- Each service `README.md`: its responsibility, port, database, and §4.1 reference. Update when the responsibility changes.
- `docs/DEVELOPMENT.md`: carries a **verification-status table** distinguishing commands that have actually been run from intended ones. Update it as commands are verified — this honesty matters more than looking finished.

## Code comments

- Comment the **why**, never the what. `// compensate for sensor clock drift measured at ingest` earns its place; `// increment i` does not.
- Match the surrounding density. Don't narrate self-evident code.
- A `package-info.java` in every backend package states that package's contract — keep it accurate when the package's role shifts.
- No commented-out code in a commit. Delete it; git remembers.

## Diagrams

Architecture diagrams are Mermaid in `docs/ARCHITECTURE.md`. When flow or dependencies change, update the diagram in the same commit. A stale diagram is worse than none — it is trusted and wrong.

## Checklist

- [ ] Every endpoint fully OpenAPI-annotated, all statuses documented
- [ ] Every DTO field has `@Schema`
- [ ] Design changes amend ARCHITECTURE.md in the same commit
- [ ] No service/topic/schema renamed without an approved doc amendment
- [ ] READMEs and the DEVELOPMENT.md verification table reflect reality
- [ ] Comments explain why; no commented-out code
- [ ] Diagrams updated alongside the change they describe
