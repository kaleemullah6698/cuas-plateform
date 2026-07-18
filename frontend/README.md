# C-UAS Operator Console

React 19 + TypeScript + Vite + Tailwind. Specification: `docs/ARCHITECTURE.md`
sections 2 (UI/UX strategy) and 3 (frontend architecture).

## Structure

| Directory | Purpose |
|---|---|
| `src/design-system/` | Tokens, primitives, patterns. Section 2.1. Built before features; never imports from `features/`. |
| `src/features/` | Vertical slices, one per domain. Features never import each other. |
| `src/realtime/` | WebSocket client and streaming stores. Section 3.4: track state lives **outside** React. |
| `src/api/generated/` | Protobuf-generated types. Do not edit by hand. |
| `src/mocks/` | MSW handlers and scenario replay, so the frontend develops with no backend. |

## Status

Scaffold only. Directory structure and build configuration exist.
No screens, components, routes, or state are implemented.
