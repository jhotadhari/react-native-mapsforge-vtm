# Layer Ordering

How z-order (draw order) works in `react-native-mapsforge-vtm`.

## Two-level ordering

Ordering operates at two levels, both following React component tree order —
a layer declared later in JSX (e.g. a `LayerMarker` after a `LayerPath`)
always renders on top, same as later siblings paint on top in the DOM.

| Level | What | Mechanism |
|---|---|---|
| **Layer** | Native `Layer` objects in `map.layers()` | JS registry → LIS-based reorder |
| **Drawable** | Individual drawables / markers within a shared fragment | `positionIndex` wired through to vtm sorting |

### Layer-level ordering

The `useLayerOrder` hook (called by every layer component during render) builds
a shared `registry.order` array tracking React tree order. On the native side,
`MapMutationQueue.reorderMinimalMoves()` syncs `map.layers()` to match this
array using a **Longest Increasing Subsequence** algorithm — only layers that
need to move are touched.

### Drawable-level ordering within fragments

Within a shared-layer fragment (e.g. 50 `<LayerPath>` components sharing one
native `VectorLayer`), individual drawables are ordered by vtm's internal
sorting. Each native manager wires `positionIndex` through to the right vtm
mechanism:

| Manager | vtm layer | Ordering |
|---|---|---|
| `PathLayerManager` | `VectorLayer` | `drawable.setPriority(positionIndex)` — sorts by `getPriority()` ascending |
| `ShapeLayerManager` | `VectorLayer` | Same — `drawable.setPriority(positionIndex)` |
| `MarkerLayerManager` | `ItemizedLayer` | Descending `positionIndex` sort before insertion — compensates for `Inlist.push()` reversal |

## How `useLayerOrder` works

Every layer component calls `useLayerOrder` during render. It:

1. Registers the component in the shared `LayerOrderRegistry`, inserting at the
   correct position using the **cursor chain** — React renders components in
   deterministic depth-first document order, so the registry cursor always
   points to the sibling that rendered immediately before this one.
2. Returns a `positionIndex` (index in `registry.order`) and a `fragmentUuid`
   (for shared-layer fragment assignment).
3. On unmount, removes the component from the registry and triggers a debounced
   native `reorderLayers` call.

### Cursor chain integrity

`MapContainer` resets `registry.cursor` to `undefined` at the start of each of
its own renders and bumps `registry.generation`. Each `useLayerOrder` call sets
`registry.cursor = id` (advancing it for the next sibling) and stamps
`layerGenerations[id] = generation`.

When a `useMemo`'d component skips re-render during a full pass, the cursor
chain has a gap. Repositioning checks that all symbols between the previous and
current position were stamped in this pass — if not, the cursor is stale and
the move is skipped.

### Scope-aware insertion

When inside a `<ReindexScope>`, new layers use scope-aware insertion instead
of the global cursor. This handles partial re-renders (e.g. Redux-triggered
data updates) where `MapContainer` doesn't re-render and the global cursor
is stale:

1. Looks up the scope's most-recently-inserted sibling from
   `lastSymbolPerScope` (O(1)) and inserts after it.
2. If no sibling found (first child), scans for the scope's sentinel
   placeholder and inserts after it.

## The registry

`MapContainer` creates a `LayerOrderRegistry`. Key fields:

| Field | Purpose |
|---|---|
| `order` | Ordered list of layer identifiers (stable `Symbol`s) |
| `cursor` | Most-recently-rendered sibling, reset each full render pass |
| `generation` | Monotonically bumped each `MapContainer` render |
| `sentinels` | Placeholder symbols for `<ReindexScope>` wrappers with no children yet |
| `layerTypes` | Layer type per symbol (for fragment grouping) |
| `fragmentIndices` / `fragmentUuids` | Per-type fragment counters and per-component fragment UUIDs |
| `lastSymbolPerScope` | Per-scope most-recently-inserted symbol (O(1) sibling lookup) |
| `scopeGenerations` | Per-scope counter bumped by `<ReindexScope>` renders |
| `scopePriorities` | `order` prop values per scope |

## Async mounting

When layers mount asynchronously (data loaded via React Query, storage restore,
etc.), the cursor from the initial render pass may be stale. Three mechanisms
preserve correct order:

1. **Sentinels** — `<ReindexScope>` pushes placeholder symbols during initial
   render even with `null` children. When children later mount, they insert at
   the sentinel's position.
2. **Scope-aware insertion** — ignores the stale global cursor, uses
   `lastSymbolPerScope` or the sentinel for the correct insertion point.
3. **`order` prop** — explicit numeric priority on `<ReindexScope>`
   (`order={100}` renders before `order={200}`), regardless of mount timing.

## Fragments and `SharedLayer`

### What are fragments?

A fragment is a native layer that hosts multiple JS-level components of the
same type. 50 `<LayerPath>` components inside a `<SharedLayer>` share one
native `VectorLayer` — each component is a drawable on that fragment.

### Fragment boundaries

Fragments split at:
- **Type-run boundaries** — consecutive same-type layers share a fragment;
  a different type starts a new one (Path → Marker → Path = 3 fragments).
- **Scope boundaries** — layers in different `<ReindexScope>` wrappers never
  share a fragment, even if same-type and consecutive in order.

### Fragment UUIDs

Pattern: `__vtm_shared_<type>__<index>` (e.g. `__vtm_shared_path__1`).
Inside a `<SharedLayer>`, the scope ID is used as the suffix so all children
share one fragment. Outside, an incrementing per-type index advances on
type-run boundaries.

## `ReindexScope`

Wraps children that need reindexing when they reorder outside React's
reconciliation flow (e.g. Redux-driven list reordering). Also provides
sentinels for async children and the `order` prop for explicit cross-scope
priority.

See the [ReindexScope docs](../components/reindex-scope.md) for full details.

## Debugging

- **[useLayerDebugInfo](../debug/use-layer-debug-info.md)** — Introspect layer order at runtime
- **[LayerDebugTree](../debug/layer-debug-tree.md)** — Visual debug overlay
