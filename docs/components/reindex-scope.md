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

## Example

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

## API

```tsx
<ReindexScope>{children}</ReindexScope>
```

- **Props**: `children?: ReactNode`
- **Context required**: Must be inside `<MapContainer>` children (at any nesting depth)
- **Returns**: A React Fragment-equivalent wrapping children in the reindex context

## Caveats

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
