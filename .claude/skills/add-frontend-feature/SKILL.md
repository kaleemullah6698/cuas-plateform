---
name: add-frontend-feature
description: Build a screen, component, feature slice, or UI element in the React operator console — design system, state management, routing, real-time streaming, dark mode, accessibility. Use when adding any frontend page, component, hook, store, or styling.
---

# Adding a frontend feature

Paths relative to `frontend/`. `docs/ARCHITECTURE.md` §2 (UI/UX) and §3 (architecture) govern.

## Before you write anything

1. Find the screen in §2.2 and its specification in §2.3 — primary goal, critical actions, keyboard shortcuts, alert prioritisation, mobile behaviour. §2.4 has ASCII wireframes for the four main screens. **Do not invent screens or interactions.**
2. Check `src/design-system/` first. A new one-off button is a defect; extend the primitive.
3. If it isn't in §2.2, stop and ask.

## Where code goes

```
src/design-system/     Tokens, primitives, patterns. Never imports from features/.
src/features/<name>/   Vertical slice: api/ components/ hooks/ routes/
src/realtime/          WebSocket client + streaming stores. Lives OUTSIDE React.
src/api/generated/     Protobuf output. Never hand-edit.
src/mocks/             MSW handlers + scenario replay — develop with no backend.
```

**Import boundaries** (ESLint-enforced): `design-system/` never imports from `features/`; features never import each other. Cross-feature communication goes through `src/store/` or the router.

## State — four domains, four tools (§3.4)

Conflating these is the usual cause of real-time dashboard collapse.

| Domain | Tool | Example |
|---|---|---|
| Server state | TanStack Query | Incidents, audit pages, users |
| **Streaming state** | **External store + `useSyncExternalStore`** | Track positions, live alerts |
| UI state | Zustand | Selection, filters, panel layout |
| Form state | React Hook Form + Zod | Admin forms, incident notes |

### The most important frontend rule

**Streaming data never passes through React state per-message.** Track updates arrive at up to 20 Hz across 200 tracks — 4,000 updates/second. They live in a plain `Map` outside React, coalesced to `requestAnimationFrame`:

```ts
applyDelta(d: TrackDelta): void {
  this.tracks.set(d.trackId, mergeTrack(this.tracks.get(d.trackId), d));
  this.dirty = true;                    // no React notification here
  RafScheduler.request(this.flush);     // coalesce to next frame
}
```

Result: 4,000 updates/second becomes at most 60 renders/second. Calling `setState` per message instead will collapse the console.

Render budgets (§2.5.1): map 10 Hz, track table 4 Hz, timeline 1 Hz, sensor health 1 Hz. **Alerts are never coalesced** — they render on arrival.

## Design language — §2.1

Use tokens from `src/design-system/tokens/`. Never a hard-coded colour, size, or spacing value.

- **Dark-first.** Operators work long shifts in low light. Dark is the primary target; light is supported secondary.
- **Colour carries meaning only.** Saturated hue is reserved for threat state and sensor status. No decorative colour, no gradients-as-decoration.
- **No stickers, mascots, or illustration.** Icons are functional glyphs from one geometric set (Lucide, 1.5px stroke, 16/20/24px).
- **Data density over whitespace** — target 3–5× a consumer SaaS dashboard.
- Spacing on the 8-point scale. Radius restrained (`0` for tables, `4` inputs, `6` cards). Elevation via surface luminance, not shadow alone.
- **Tabular figures** (`JetBrains Mono`) for all numerics, so streaming values don't jitter horizontally.

### Threat state — never colour alone

Every threat indicator carries three redundant channels:

| Threat | Colour | Shape | Label |
|---|---|---|---|
| Critical | Red | ▲ | `CRIT` |
| High | Orange | ◆ | `HIGH` |
| Medium | Amber | ■ | `MED` |
| Low | Green | ● | `LOW` |
| Unknown | Grey | ○ | `UNK` |

Confidence is encoded as opacity + a numeric percentage — **never** as a hue. Hue means threat. Use the `ConfidenceBar` pattern; its fill is neutral by design, and absent confidence renders `——`, never `0%`.

## Accessibility — WCAG 2.1 AA, §2.1.7

Not optional.

- Text contrast ≥ 4.5:1, non-text ≥ 3:1 — tokens are pre-validated
- Every action keyboard-reachable, including map track cycling. No focus traps.
- Visible focus ring, 2px, never suppressed
- `prefers-reduced-motion` disables pulse and flash; alerts fall back to a static border plus audio
- Nothing flashes above 3 Hz (critical alert pulse is 0.8 Hz)
- New critical alerts announce via `aria-live="assertive"`; track updates via a throttled `polite` summary region — never per-update
- Interactive targets ≥ 44×44px on primary paths

## UI components

ShadCN-style components are acceptable **only re-themed onto the §2.1 tokens**. Stock ShadCN ships a light-first, heavily-rounded consumer look that contradicts §2.1 — do not paste it in unmodified. Underlying primitives (Radix, `class-variance-authority`, `tailwind-merge`) are already in `package.json`.

## Data fetching

Use the `src/api/client.ts` fetch wrapper — it handles the auth header, refresh-on-401, and correlation-ID propagation. Axios is not used in this project (§3.1); do not add it.

## Developing with no backend

`src/mocks/` provides MSW handlers and recorded scenarios: `single-intrusion`, `swarm`, `false-positive-bird`, `sensor-failure`. Build and test the full UI before any service exists.

## Verification

```bash
cd frontend
npm run typecheck
npm run test
npm run dev        # http://localhost:3000
```

**Note:** `npm ci` has never been executed in this repository. Dependency versions are declared but unresolved; expect to fix some on first install.

## Checklist

- [ ] Screen appears in §2.2; behaviour matches its §2.3 specification
- [ ] Keyboard shortcuts implemented as specified
- [ ] Design-system tokens used — no hard-coded colours or spacing
- [ ] Correct state tool for the domain
- [ ] Streaming data bypasses React state
- [ ] Threat state carries colour + shape + label
- [ ] Dark and light both render correctly
- [ ] Keyboard-navigable, focus visible, `prefers-reduced-motion` respected
- [ ] Loading, empty, and error states all present