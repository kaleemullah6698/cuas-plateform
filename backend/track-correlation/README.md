# track-correlation

Detection association, Kalman state estimation, track lifecycle, CPA

- **Port:** 8084
- **Database:** trackdb
- **Specification:** `docs/ARCHITECTURE.md` section 4.1

## Status

Scaffold only. Package structure follows ARCHITECTURE.md section 4.3
(Clean/hexagonal): `domain/` holds framework-free business logic,
`api/` and `infrastructure/` are adapters pointing inward at it.

No endpoints, entities, repositories, or event handlers are implemented.
