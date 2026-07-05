# ReindexScope

Wraps children in a scope whose layers are reindexed together. Signals that the
current render pass should reindex already-registered layers in the native layer
stack to match their current React tree document order.

## When to use it

- **Redux / Zustand layer lists** — The parent subscribes to a slice of state
  that changes the layer array order, but `MapContainer` does not re-render
  because its own props haven't changed.
- **Memoized children** — `React.memo()` or `useMemo()` prevents individual
  layer components from re-rendering in response to an ancestor's state change
  that reorders them.
- **Programmatic reorder** — Layers are sorted or rearranged imperatively
  outside React's normal render flow and the existing DOM order must be
  reflected on the native side.
- **Async children** — Children mount later due to async data (React Query,
  storage restore, network). The sentinel mechanism (see below) ensures
  correct positioning relative to sibling scopes.

## When NOT to use it

- **Static JSX** — If your layers are declared once in JSX and never reorder,
  `MapContainer` already re-registers on every one of its own render passes.
- **Conditional toggling** — Mounting/unmounting a layer (via `{cond && <LayerPath/>}`)
  triggers the create/remove lifecycle, which handles insertion position
  automatically. No reindex needed.
- **Adding or removing layers** — The `positionIndex` computed by
  `useLayerOrder` during `createLayer` places new layers at the correct
  position from the start.

## How it works

ReindexScope uses a two-phase protocol:

**Phase 1 (render):** Finds its scope-tagged layers at their current positions
in the live layer order, records the starting position, sets the cursor so
children reposition correctly, and bumps the generation counter.

**Phase 2 (useLayoutEffect):** After children render, verifies the block is
at the correct position. If the cursor chain broke (e.g., `React.memo`'d
children), corrects the relative order using the pre-render order captured
in Phase 1. Calls the debounced native sync.

### Sentinel mechanism

When a `<ReindexScope>` renders **without children** (async data still
loading), it pushes a sentinel placeholder symbol into the registry's
`order` array. Sibling scopes and non-scoped layers immediately see the
correct relative position — the sentinel marks where this scope's block
will go. When children eventually mount, the sentinel is removed and
children insert at its position.

**Important:** The sentinel is only placed when the `<ReindexScope>` wrapper
itself renders during MapContainer's initial render pass. If the wrapper is
conditionally rendered (returns `null` before the `<ReindexScope>` element),
no sentinel gets placed — use the `order` prop as a fallback.

## Ordering: JSX tree vs `order` prop

The position of a scope's layers in the z-order stack is determined by one
of two mechanisms:

| Mechanism | When it applies | How position is determined |
|-----------|----------------|---------------------------|
| **JSX tree (cursor chain)** | Initial render pass, or any pass where MapContainer re-renders | React's depth-first render order — later siblings appear later in `registry.order` (higher z-index) |
| **`order` prop** | Any render pass (always takes priority when set) | Explicit numeric value — `order={100}` always renders before `order={200}`, regardless of JSX position |

Without the `order` prop, position follows the JSX tree **during the initial
render pass only**. After that, MapContainer is typically memoized and cursor
is stale — newly mounted layers append at the end regardless of tree position.
The sentinel mechanism handles the common case (scope renders at initial mount,
children arrive later). The `order` prop handles the harder case (scope itself
mounts late, or you need explicit priority control).

## Example

### Basic: layers reordering in a list

```tsx
import { ReindexScope, MapContainer, LayerPath } from 'react-native-mapsforge-vtm';

const LayerList = ({ paths }) => (
  <ReindexScope>
    {paths.map((path) => (
      <LayerPath
        key={path.id}
        coordinates={path.coordinates}
        style={path.style}
      />
    ))}
  </ReindexScope>
);

const App = () => {
  const paths = useSelector((state) => state.paths);

  return (
    <MapContainer center={[-77.6, -9.1]} zoomLevel={12}>
      <LayerList paths={paths} />
      <LayerBitmapTile
        url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
        zoomMax={18}
      />
    </MapContainer>
  );
};
```

### With `order` prop: explicit priority across scopes

```tsx
const App = () => {
  const { baseMapReady, lines, routes } = useAsyncData();

  return (
    <MapContainer center={[-77, -9]} zoomLevel={8}>
      {/* baseMapReady may resolve last — sentinel holds its position */}
      <ReindexScope order={100}>
        {baseMapReady && <LayerBitmapTile url="..." />}
      </ReindexScope>

      <ReindexScope order={200}>
        {lines.map((l) => (
          <LayerPath key={l.id} coordinates={l.coords} />
        ))}
      </ReindexScope>

      <ReindexScope order={300}>
        {routes.map((r) => (
          <LayerPath key={r.id} coordinates={r.coords} />
        ))}
      </ReindexScope>

      <LayerScalebar />
    </MapContainer>
  );
};
```

**Always render the `<ReindexScope>` wrapper** — even when data hasn't loaded
yet. Put the conditional _inside_ the wrapper:

```tsx
// BAD: scope doesn't render → no sentinel → wrong position on late data
if (!points) return null;
return <ReindexScope>...</ReindexScope>;

// GOOD: scope always renders → sentinel holds position
<ReindexScope order={200}>
  {points && <LayerPath ... />}
</ReindexScope>
```

## API

```tsx
type ReindexScopeProps = {
  children?: ReactNode;
  /**
   * Optional priority for ordering across sibling ReindexScope instances.
   * Lower values = earlier in the layer order = lower z-index on the map.
   *
   * When set, position is determined by comparing this value with other
   * scopes' `order` values, NOT by JSX tree position.
   *
   * Scopes without an `order` prop use JSX tree order (cursor chain) during
   * the initial render pass. After that, position may degrade for async
   * children unless the sentinel mechanism covers it.
   */
  order?: number;
};
```

```tsx
<ReindexScope order={100}>{children}</ReindexScope>
```

- **Props**: `children?: ReactNode`, `order?: number`
- **Context required**: Must be inside `<MapContainer>` children (at any nesting depth)
- **Returns**: A React Fragment-equivalent wrapping children in the reindex context

## Caveats

- **Always render the wrapper** — To get correct positioning for async children,
  render `<ReindexScope>` in the initial pass (with `null` children if needed).
  If the wrapper itself mounts late, use the `order` prop.
- **`order` overrides JSX tree** — When `order` is set, the JSX position of the
  `<ReindexScope>` tag is irrelevant. `order={100}` always renders before
  `order={200}`. Combine with sentinel positioning for maximum correctness.
- **Safe to nest** — Nested ReindexScopes each manage their own sub-range
  independently. Outer scopes shift entire blocks; inner scopes shift within
  their parent's block.
- **Works with SharedLayer** — ReindexScope manages position ranges;
  SharedLayer manages fragment assignment. They are orthogonal. A layer can
  be in neither, either, or both.
- **Avoid memoizing layers inside** — If a layer inside ReindexScope skips
  render (e.g., `React.memo`), the cursor chain breaks. Phase 2 corrects
  this using the pre-render order, but for optimal accuracy, let all children
  re-render when the scope re-renders.
- **No performance overhead** — The common case (no position change) is a
  single O(N) validation scan (~0.3ms for 5,000 layers). Splice work only
  fires on actual position changes.

## See also

- **[Layer Ordering](../advanced/layer-ordering.md)** — Full explanation of
  the z-order model, fragment infrastructure, and `SharedLayer`
- **[SharedLayer](../components/shared-layer.md)** — Collapse same-type
  children into shared native layer fragments
- **[MapContainer](../components/map-container.md)** — The root map view
  that hosts the layer registry
- **[getDebugLayerDump()](../debug/get-debug-layer-dump.md)** — Debug the
  layer order, including sentinel count
