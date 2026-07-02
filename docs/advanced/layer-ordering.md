# Layer Ordering

How z-order (draw order) works in `react-native-mapsforge-vtm`.

## The invariant

**Native layer rendering order must strictly follow React component tree
order.** A layer declared later in JSX (e.g. a `LayerMarker` mounted after a
`LayerPath`) must always render on top, same as later siblings paint on top
in the DOM.

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
- **`layerTypes`** — maps identifier → layer type string (for fragment
  grouping)
- **`fragmentIndices`** — maps layer type → next fragment index (for
  `SharedLayer`)
- **`generation`** — bumped each render pass so `useLayerOrder` can
  distinguish a full coherent render from a solo re-render
- **`cursor`** — current position in the order (reset on each render pass)

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

### Fragment UUIDs

Fragment UUIDs follow the pattern `<type>-<index>`, e.g. `path-0`,
`path-1`. When `SharedLayer` is active, consecutive same-type children get
the same fragment UUID.

### Why fragments exist

Without fragments, every layer component creates its own native `Layer`
object. With 1000 `<LayerPath>` components, that's 1000 native `Layer`
objects + 1000 GPU draw calls. Fragments collapse them into ~1–10 native
layers.

## ReindexScope

When layers reorder outside React's normal reconciliation flow (e.g.
Redux-driven list reordering where children are `React.memo`'d), use
`<ReindexScope>` to signal that the native layer stack needs reindexing.

See the [ReindexScope docs](../components/reindex-scope.md) for full details.

## Known bug

**Layer render order doesn't strictly follow React tree hierarchy under
load.** See `TODO.md` item 0. This affects `LayerPath` (shared-layer) more
than `LayerPathJts` (dedicated-layer), because shared-layer drawable UUIDs
aren't in the `knownLayers` map that `reorderLayers` references.

## See also

- **[ReindexScope](../components/reindex-scope.md)** — Signal reorder to the native stack
- **[SharedLayer](../components/shared-layer.md)** — Fragment grouping
- **[useLayerDebugInfo](../debug/use-layer-debug-info.md)** — Introspect layer order at runtime
