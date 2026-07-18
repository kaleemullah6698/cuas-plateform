# Infrastructure

Infrastructure as code (ARCHITECTURE.md section 8.6). Everything here is
version-controlled configuration, not hand-applied state.

| Directory | Contents | Status |
|---|---|---|
| `docker/` | Shared Docker assets | Placeholder |
| `kubernetes/` | Kustomize base and overlays | Placeholder — section 8.3 marks K8s as future, not required for the prototype |
| `postgres/` | `init-databases.sh` creating the ten per-service schemas | Placeholder |
| `kafka/` | `create-topics.sh` declaring the section 5.2 topic catalogue | Placeholder |
| `redis/` | Redis configuration | Placeholder |
| `elasticsearch/` | Index templates and ILM policies implementing section 7.9 retention | Placeholder |
| `monitoring/` | Prometheus rules, Grafana dashboards, Loki, Tempo | Placeholder |

## Not merely placeholders

Three of these are declarative configuration the system depends on, and should
be filled first:

- **`kafka/create-topics.sh`** — topic auto-creation is disabled by design
  (section 8.2), so every topic in the section 5.2 catalogue must be declared.
- **`postgres/init-databases.sh`** — database-per-service (section 4.1) means
  ten separate schemas, created at container init.
- **`elasticsearch/ilm-policies/`** — retention per section 7.9. Audit data is
  never automatically deleted.
