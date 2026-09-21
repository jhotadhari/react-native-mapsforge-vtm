# Layer Ordering

How z-order (draw order) works in `react-native-mapsforge-vtm`.

## Overview

Ordering is a **function of committed state, never accumulated during render**.
`MapContainer` exposes a `LayerScene` (the single source of truth) plus a
`SceneSync` presenter through `MapHandleContext`. Layer components render
invisible `VtmAnchorView` host components (anchors) and declare entries into
the scene via commit-phase hooks. The committed view tree is walked to produce
the tree order, which drives an absolute native reorder.

The invariant is the same as the DOM: a layer declared later in JSX (e.g. a
`LayerMarker` after a `LayerPath`) always renders on top.

## Two-level ordering

Ordering operates at two levels, both following React component tree order.

| Level | What | Mechanism |
|---|---|---|
| **Layer stack** | Native `Layer` objects in `map.layers()` (~5–30: scopes, fragments, dedicated layers) | anchor walk → scene plan → absolute `reorderLayers` (LIS) |
| **Drawables** | Individual drawables / markers within a shared fragment (1000s of entries) | fragment owner declares ordered entry keys → sparse priorities → `applyEntryPriorities` |

## The scene model

### Anchors and the walk

`useLayerAnchor` (`src/compose/useLayerAnchor.tsx`) renders an invisible 0×0
`VtmAnchorView` and registers an `AnchorDescriptor` with `SceneSync`. Scopes,
fragment owners (`SharedLayer` / `LayerMarker`), and standalone layer
components each render one anchor.

`MapContainer.enumerateAnchors` walks the committed view tree under the map's
wrapper `View` and returns the ordered anchor uids — this is the "walk". A
hierarchy-change listener on the wrapper emits `onAnchorsChanged` as the move
signal when anchors are added/removed/reordered.

### `LayerScene`

`LayerScene` (`src/scene/LayerScene.ts`) is mutated ONLY from the commit phase
(`useLayoutEffect`/`useEffect`, never render — React renders may be partial or
discarded). Mutations:

| Method | What it does |
|---|---|
| `applyWalk(sequence)` | Replaces the committed anchor sequence (a fresh walk result). |
| `declareEntry(entry)` / `undeclareEntry(uid)` | Declares/removes an entry (drawable) inside a fragment owner. |
| `attachUuid(key, uuid)` / `detachUuid(key)` | Records/clears a resolved native uuid for an anchor or entry. |

`plan()` returns a cached, immutable `LayerPlan`; `version()` is a monotonic
counter bumped on every mutation (used by the presenter to detect changes).

### `planBuilder`

`buildPlan(walk, entries, uuids)` (`src/scene/planBuilder.ts`) is a pure
function turning committed state into a bottom→top `LayerPlan`:

- `layers` — the ordered native-layer uuid list.
- `fragments` — per-fragment entry lists (all declared `entryUids` + resolved subset).
- `scopes` — scope info (uid, `order`, anchor index).
- `runKeysByAnchor` — the scene-authoritative fragment uuid per type-run member.

Fragment keys are **deterministic**:

- `frag:<owner>:<type>` for `SharedLayer`/`LayerMarker` fragments (e.g. `frag:<sharedId>:path`).
- `run:<anchor>` for implicit type-run fragments (standalone same-type layers not wrapped in `SharedLayer`, keyed by the run's first member).

### `SceneSync` (presenter)

`SceneSync` (`src/scene/SceneSync.ts`) subscribes to the scene and drives the
native side:

1. A **debounced walk** (16ms / 250ms max-wait) calls `enumerateAnchors` and
   feeds the result to `scene.applyWalk`.
2. Every scene mutation schedules a **sync**: `scene.plan()` → `diffPlans`
   against the last-applied plan → one absolute `reorderLayers` call plus
   per-fragment `applyEntryPriorities` (single-flight, exponential backoff).

The scene's `subscribe`/`version()` drive scheduling — no cursor, sentinel, or
generation machinery.

## Drawable ordering within fragments

Within a shared-layer fragment (e.g. 50 `<LayerPath>` components sharing one
native `VectorLayer`), individual drawables are ordered by vtm's internal
sorting. Each native manager wires a sparse **priority** through to the right
vtm mechanism:

| Manager | vtm layer | Ordering |
|---|---|---|
| `PathLayerManager` | `VectorLayer` | `drawable.setPriority(priority)` — sorts by `getPriority()` ascending |
| `ShapeLayerManager` | `VectorLayer` | Same — `drawable.setPriority(priority)` |
| `MarkerLayerManager` | `ItemizedLayer` | Descending `positionIndex` sort; equal priorities break by ascending `creationSeq` |

### `PriorityAllocator`

`PriorityAllocator` (`src/scene/PriorityAllocator.ts`) assigns sparse integer
priorities (step 1000, midpoint insertion) so a single insert/remove
re-prioritizes O(changed) entries rather than the whole fragment. When a
midpoint gap is exhausted — or priorities drift outside a safe band — the whole
fragment is renumbered once (amortized O(1)).

Priorities are applied natively via `applyEntryPriorities`, which returns
`false` when the fragment is missing so the presenter rejects and re-sends.

## Entry injection

Entries are ordered by an owner-injected `vtmSortIndex` (recursively injected
through arrays and `Fragment`s by `injectVtmSortIndex`) or the explicit `order`
prop. Unindexed entries sort by declaration sequence.

## Async mounting

When layers mount asynchronously (data loaded via React Query, storage restore,
etc.), correct order is preserved without sentinels:

1. **Scope anchors** — a `<ReindexScope>` renders an anchor that permanently
   marks the block's tree position; children that mount later land at their
   correct tree position (the anchor IS the placeholder).
2. **`order` prop** — explicit numeric priority on `<ReindexScope>`
   (`order={100}` renders before `order={200}`), regardless of mount timing.
3. **Debounced re-walk** — anchor changes re-trigger the walk, and the plan is
   re-applied atomically.

## Fragments

### What are fragments?

A fragment is a native layer that hosts multiple JS-level components of the
same type. 50 `<LayerPath>` components inside a `<SharedLayer>` share one
native `VectorLayer` — each component is a drawable on that fragment.

### Fragment boundaries

Fragments split at:

- **Type-run boundaries** — consecutive same-type layers share a fragment; a
  different type starts a new one (Path → Marker → Path = 3 fragments).
- **Scope boundaries** — layers in different `<ReindexScope>` wrappers never
  share a fragment, even if same-type and consecutive in order.

### Fragment UUIDs

- `frag:<owner>:<type>` for `SharedLayer`/`LayerMarker` fragments (e.g. `frag:<sharedId>:path`).
- `run:<anchor>` for implicit type-run fragments, keyed by the run's first member.

## `ReindexScope`

Wraps children that need reindexing when they reorder outside React's
reconciliation flow (e.g. Redux-driven list reordering). It renders a scope
anchor marking the block's tree position and carries the `order` prop for
explicit cross-scope priority.

See the [ReindexScope docs](../components/reindex-scope.md) for full details.

## Debugging

- **[useLayerDebugInfo](../debug/use-layer-debug-info.md)** — Introspect layer order at runtime
- **[LayerDebugTree](../debug/layer-debug-tree.md)** — Visual debug overlay
- **[getDebugLayerDump](../debug/get-debug-layer-dump.md)** — Native ground truth vs the scene plan
