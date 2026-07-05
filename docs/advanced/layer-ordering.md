# Layer Ordering

How z-order (draw order) works in `react-native-mapsforge-vtm`.

## The invariant

**Native layer rendering order must follow React component tree order.**
A layer declared later in JSX (e.g. a `LayerMarker` mounted after a
`LayerPath`) must always render on top, same as later siblings paint on top
in the DOM.

### Async mounting

This invariant is maintained **within a single render pass** — React calls
component render functions in deterministic depth-first document order.
When all layers mount synchronously during MapContainer's initial render,
the cursor chain produces the correct order automatically.

When layers mount **asynchronously** (data loaded via React Query, storage
restore, network), the cursor from the initial render pass is stale
(MapContainer is typically memoized and doesn't re-render). Two mechanisms
preserve the invariant:

1. **Sentinels** — `<ReindexScope>` wrappers push placeholder symbols into
   the registry's order array during the initial render (even with `null`
   children). When children later mount, they insert at the sentinel's
   position — their correct tree position.
2. **`order` prop** — An explicit numeric priority on `<ReindexScope>`
   (`order={100}` always before `order={200}`) that determines position
   regardless of when the scope or its children mount.

Without either mechanism, layers mount at the cursor position (end of order)
which is only correct during MapContainer's initial render pass.

## How it works

### Registration via `useLayerOrder`

Every layer component (`LayerMapsforge`, `LayerPath`, `LayerMarker`, etc.)
calls `useLayerOrder` during render. This hook:

1. Computes a **position index** — where this component sits in the React
   tree's document order, relative to all other layers
2. Registers the component's position in the shared `LayerOrderRegistry`
3. Passes the position index to the native `createLayer` call
4. If already registered, checks whether the position changed and (if so)
   triggers a debounced `reorderLayers` call

### The registry

`MapContainer` creates a `LayerOrderRegistry` per render pass. It tracks:

- **`order`** — ordered list of layer identifiers (stable `Symbol`s)
- **`sentinels`** — sentinel placeholders for ReindexScope wrappers awaiting
  children (skipped by native flush)
- **`layerTypes`** — maps identifier → layer type string (for fragment
  grouping)
- **`fragmentIndices`** — maps layer type → next fragment index (for
  `SharedLayer`)
- **`generation`** — bumped each render pass so `useLayerOrder` can
  distinguish a full coherent render from a solo re-render
- **`cursor`** — current position in the order (reset on each render pass)
- **`scopePriorities`** — maps ReindexScope symbol → `order` prop value

### Native side: `MapMutationQueue.flush()`

On the native side, `MapMutationQueue` serializes layer mutations
(create, remove, reorder) on the UI thread. Reordering uses the **LIS
(Longest Increasing Subsequence) algorithm** — it computes the minimal set
of layer moves to transform the current order into the target order, calling
`layers().add(index, layer)` only for layers that actually moved.

## Fragment infrastructure

### What are fragments?

A "fragment" is a native layer that hosts multiple JS-level components of the
same type. For example, 50 `<LayerPath>` components inside a `<SharedLayer>`
share one native `VectorLayer` fragment. Each component is a "drawable" on
that fragment.

### Fragment boundaries

Fragments are separated at:
- **Type-run boundaries** — consecutive same-type layers share a fragment;
  a different type starts a new one (e.g. Path → Marker → Path = 3 fragments)
- **Scope boundaries** — layers in different ReindexScopes never share a
  fragment, even if they're the same type and consecutive in order. This
  prevents one scope's re-render from moving another scope's layers.

### Fragment UUIDs

Fragment UUIDs follow the pattern `__vtm_shared_<type>__<index>`, e.g.
`__vtm_shared_path__1`. When `SharedLayer` is active, consecutive same-type
children get the same fragment UUID.

### Why fragments exist

Without fragments, every layer component creates its own native `Layer`
object. With 1000 `<LayerPath>` components, that's 1000 native `Layer`
objects + 1000 GPU draw calls. Fragments collapse them into ~1–10 native
layers.

## ReindexScope

When layers reorder outside React's normal reconciliation flow (e.g.
Redux-driven list reordering where children are `React.memo`'d), use
`<ReindexScope>` to signal that the native layer stack needs reindexing.

For async children, `<ReindexScope>` also provides the sentinel mechanism
and optional `order` prop for explicit priority control across scopes.

See the [ReindexScope docs](../components/reindex-scope.md) for full details.

## Known limitations

**Layer render order under async load.** When multiple ReindexScope wrappers
render with children that mount at different times (async data), the cursor
chain from the initial render pass is stale. The sentinel mechanism (commit
`c2944f8`) addresses the common case where the scope wrapper renders at
initial mount. The `order` prop covers the harder case where the scope itself
mounts late. See [ReindexScope](../components/reindex-scope.md#ordering-jsx-tree-vs-order-prop).

**Shared-layer drawable UUIDs.** `LayerPath` (shared-layer) drawable UUIDs
use fragment UUIDs which resolve via the JS-side ordering registry. When
the registry and native layer list diverge (pending mutations > 0), the
order may be temporarily inconsistent. `LayerPathJts` (dedicated-layer) is
not affected.

## See also

- **[ReindexScope](../components/reindex-scope.md)** — Sentinel mechanism, `order` prop, and reindex protocol
- **[SharedLayer](../components/shared-layer.md)** — Fragment grouping
- **[useLayerDebugInfo](../debug/use-layer-debug-info.md)** — Introspect layer order at runtime
- **[getDebugLayerDump()](../debug/get-debug-layer-dump.md)** — Full native + registry dump with sentinel count
