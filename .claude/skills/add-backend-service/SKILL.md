---
name: add-backend-service
description: Build, implement, or extend a Spring Boot backend microservice in this platform — package layout, POM, configuration, persistence, Kafka messaging, gRPC. Use when creating a new service, fleshing out a scaffolded one, adding a domain model, repository, entity, mapper, consumer, or producer.
---

# Adding or extending a backend service

Paths are relative to the repository root. `docs/ARCHITECTURE.md` §4 governs.

## Before you write anything

1. Find the service in §4.1. It defines responsibilities, database ownership, APIs, events published, events consumed, and scaling characteristics. **Do not invent any of these.**
2. Check `backend/platform-commons/` — correlation IDs, logging, metrics, tracing, exception mapping, and validation are already the shared home. Do not reimplement them per service.
3. If the service does not exist in §4.1, stop and ask. Adding a thirteenth service is an architecture change.

## Package layout — §4.3

Every service uses this tree under `src/main/java/pk/nust/cuas/<pkg>/`:

```
api/rest            Controllers. Translate HTTP, delegate, return DTOs.
api/grpc            gRPC service implementations from contracts/proto.
api/dto             Request/response records. The wire contract.
domain/model        Aggregates, entities, value objects. Pure Java.
domain/service      Logic spanning more than one aggregate.
domain/policy       Business rules and invariants as first-class objects.
infrastructure/persistence/entity      JPA entities.
infrastructure/persistence/repository  Spring Data implementations.
infrastructure/persistence/mapper      Domain <-> entity mapping.
infrastructure/messaging/consumer      Kafka consumers.
infrastructure/messaging/producer      Kafka producers.
infrastructure/messaging/serde         Protobuf serialisation.
infrastructure/cache                   Redis adapters.
infrastructure/client                  Outbound gRPC/REST clients.
config                                 Spring wiring.
common                                 Service-local cross-cutting only.
```

Each leaf already has a `package-info.java` stating its contract. Read it.

### The one rule that matters

`domain/` must compile with Spring off the classpath.

```java
// domain/model/Track.java — correct
public record Track(TrackId id, GeoPosition position, ThreatLevel level) {
    public Track {
        Objects.requireNonNull(id, "id");
    }
}
```

```java
// WRONG — framework annotation in the domain
@Entity
public class Track { @Id private Long id; }
```

Domain models and JPA entities are separate types. `TrackEntity` lives in `infrastructure/persistence/entity/`, and `TrackMapper` translates. The duplication is deliberate: it lets the schema change without the domain changing.

Repository interfaces are declared in `domain/`, implemented in `infrastructure/`:

```java
// domain/TrackRepository.java — the domain states what it needs
public interface TrackRepository {
    Optional<Track> findById(TrackId id);
    Track save(Track track);
}
```

## Adding a domain model

1. Record or class in `domain/model/`. Validate invariants in the compact constructor — an invalid instance must be unconstructable.
2. Value objects for anything with rules (`TrackId`, `ThreatScore`, `GeoPosition`), not bare `String`/`double`.
3. Unit test with plain JUnit — no Spring context, no database.

## Adding persistence

1. `TrackEntity` in `infrastructure/persistence/entity/` — shaped by the database, not the domain.
2. Flyway migration in `src/main/resources/db/migration/` as `V<n>__<description>.sql`. Forward-only; never edit an applied migration.
3. Spring Data repository in `infrastructure/persistence/repository/`, implementing the domain interface.
4. MapStruct mapper in `infrastructure/persistence/mapper/`.
5. Integration test with Testcontainers against real PostgreSQL — never an in-memory substitute.

The database name comes from §4.1. A service touches **only** its own database.

## Adding Kafka messaging

1. Confirm the topic exists in the §5.2 catalogue. If not, stop and ask — topic auto-creation is disabled by design, and topics are declared in `infrastructure/kafka/create-topics.sh`.
2. Message schemas are Protobuf from `contracts/proto/`. Never hand-roll a payload class.
3. Consumers in `infrastructure/messaging/consumer/`, producers in `infrastructure/messaging/producer/`.
4. **Consumers must be idempotent.** Delivery semantics are per-stage in §5.7 — read the row for your stage before choosing a guarantee.
5. Every consumer needs a DLQ path. Configure back-pressure per §5.6: pause partitions on a full internal queue; never let ingestion stop.

## Configuration

`application.yml` holds the shape; profile files hold overrides. Secrets come from environment variables, never literals:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/trackdb
    password: ${POSTGRES_PASSWORD}
```

Runtime-tunable values belong in `config-service` (§4.1.12), pushed via `WatchConfig` — not in `application.yml`, which requires a redeploy.

## Verification

```bash
cd backend
mvn -B -pl <service> -am verify
```

**Note:** this has never been executed successfully in this repository — no JDK or Maven was available when the scaffold was generated. Expect to fix POM coordinates and dependency versions on the first real build, and correct `docs/DEVELOPMENT.md` when you do.

## Checklist

- [ ] Service exists in §4.1; responsibilities match
- [ ] `domain/` imports no framework
- [ ] Domain and JPA entity are separate types with an explicit mapper
- [ ] Repository interface in `domain/`, implementation in `infrastructure/`
- [ ] Constructor injection everywhere
- [ ] Flyway migration is forward-only
- [ ] Kafka topics exist in §5.2; consumers are idempotent
- [ ] No secret in a config file
- [ ] Domain unit tests without Spring; integration tests with Testcontainers
