# api-gateway

Single authenticated ingress for the platform.

- **Port:** 8080
- **Datastore:** none — owns no domain data. Redis holds rate-limit buckets and the revocation denylist.
- **Specification:** `docs/ARCHITECTURE.md` sections 4.1.1, 7.3, 7.4, 7.8

## What a request goes through

```
correlation ID  →  access log  →  fail-closed guard
    →  JWKS signature / issuer / audience / revocation
    →  section 7.8 token buckets
    →  section 7.4 permission pre-check
    →  proxy to the owning service, path unchanged
```

Order is load-bearing. The token is decoded before the quota is spent, so a request is charged to
its user rather than its address; the quota is spent before the pre-check, so an authorised but
excessive caller is still refused; the pre-check runs last, so a `403` is only ever produced for a
caller who has been positively identified.

## Three things worth knowing before changing this service

**The permission pre-check may refuse but never grant.** `RoutePermissionPolicy` mirrors the
`@PreAuthorize` annotations of the services behind it. A route it has no rule for is *forwarded*,
still authenticated, for the owning service to judge — an absent rule is not an unguarded route.
The service-level annotation stays authoritative (section 7.4); this layer only saves a network
hop. Adding a rule here that is stricter than the endpoint's own would produce a 403 invisible in
that service's logs.

**It fails closed.** Section 7.8 specifies the limits but not what to do when Redis is unreachable.
This gateway answers `503`. Failing open would make the rate limiter switchable-off by anyone able
to disturb Redis, and would let a token revoked seconds earlier be accepted. Section 4.1 already
lists Redis as the gateway's datastore, so an unreachable Redis is an unready gateway. The
readiness probe reflects this, and an instance in that state should be taken out of rotation rather
than restarted.

**The token bucket is a Lua script, not Java.** The gateway scales horizontally. Refill-and-consume
done as read-modify-write in application code is atomic per process only, so three instances would
each grant the full quota. See `src/main/resources/redis/token-bucket.lua`.

## Section 7.8 coverage

| Row | Status |
|---|---|
| `POST /auth/login` per IP — 5/min, burst 5 | Enforced |
| `POST /auth/login` per account — 10/hour, burst 3 | Enforced (account read from the buffered body) |
| Authenticated API per user — 300/min, burst 50 | Enforced |
| `POST /reports/generate` per user — 10/hour, burst 2 | Rule declared; matches once M8 adds the endpoint |
| `POST /audit/export` per user — 5/hour, burst 1 | Rule declared; matches once the endpoint exists |
| `POST /ingest/detections` per sensor — 5,000/min | **Not implemented.** Keyed on a sensor id that does not exist at the edge until M4 |
| WebSocket connections per user — 5 concurrent | **Not implemented.** A concurrency cap, not a bucket; lands with the WS fan-out in M8 |

The *burst* column is the bucket capacity and *limit/window* is the refill rate. Read the other way
round — capacity = limit + burst — `/auth/login` would accept ten immediate attempts where the
table says five.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `gateway.auth.jwk-set-uri` | `http://localhost:8081/auth/jwks` | Key set, cached; never called per request |
| `gateway.auth.issuer` / `.audience` | `https://cuas.nust.edu.pk/auth` / `cuas-api` | Must match `AccessTokenIssuer` |
| `gateway.trusted-proxies` | empty | Peers whose `X-Forwarded-For` may be believed. **Empty means never** — an unconfigured deployment cannot be spoofed into resetting a per-IP quota |
| `gateway.cors.allowed-origins` | empty | Empty serves no browser rather than all of them |
| `gateway.access-log.sample-rate` | `0.1` | Fraction of successful requests published to Kafka. Rejections are always published |
| `gateway.routes[]` | auth, user, audit | Upstream addresses. Environment-specific, so configured rather than compiled |

Key rotation needs no configuration: Nimbus refetches the key set on an unrecognised `kid`, so a
rotated signing key is picked up on first use while the previous key stays cached for the overlap
window (section 7.3).

## Not in this service

WebSocket upgrade and fan-out are listed under section 4.1.1 but are delivered in M8, together with
the track and alert streams they carry. TLS termination is section 7.2 and is an ingress concern
configured per environment, not application code.
