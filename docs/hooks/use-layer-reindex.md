# useLayerReindex

Signals that the current render pass should reindex already-registered layers
in the native layer stack to match their current React tree document order.
Useful when layer children have been reordered without `MapContainer` itself
re-rendering.

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
  position from the start. The debounced `reorderLayers` acts only as a
  safety net.

## How it works

1. **Bumps the generation counter** — `LayerOrderRegistry.generation++` is the
   signal `useLayerOrder` checks to distinguish a full render pass (where
   already-registered layers must be repositioned) from a solo re-render of a
   single layer (where they must not be disturbed).
2. **Resets the cursor** — `registry.cursor = undefined` ensures the first
   child in this render pass anchors at position 0 rather than using the stale
   cursor from the previous pass.
3. **Debounced native sync** — After repositioning, `useLayerOrder` calls
   `registry.scheduleSync()`, which coalesces multiple sibling calls into a
   single native `reorderLayers` call — and skips it entirely when the UUID
   list hasn't changed.

## Example

```tsx
import { useLayerReindex, MapContainer, LayerPath } from 'react-native-mapsforge-vtm';

const LayerList = ({ paths }) => {
  // Call during render, BEFORE returning children.
  // Safe to call unconditionally on every render.
  useLayerReindex();

  return (
    <>
      {paths.map((path) => (
        <LayerPath
          key={path.id}
          coordinates={path.coordinates}
          style={path.style}
        />
      ))}
    </>
  );
};

// Parent: layers are driven by Redux state. When the `paths` array is
// reordered but MapContainer's own props haven't changed, useLayerReindex
// ensures the native z-order matches the new JSX document order.
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

```ts
function useLayerReindex(): void;
```

- **Arguments**: None
- **Returns**: Nothing
- **Context required**: Must be called inside a component that is a descendant
  of `<MapHandleContext.Provider>`, i.e. inside `<MapContainer>` children (at
  any nesting depth). Calling outside the context is harmless but a no-op.
- **Timing**: Call during render, before returning JSX children. Do not call
  inside `useEffect` or event handlers — those run after the render phase and
  the generation bump would not affect the already-completed render pass.

## Caveats

- **Safe to call unconditionally** — The repositioning logic is a no-op when
  the document order hasn't changed. The debounced native sync skips the
  bridge call when the UUID list is unchanged. You can call `useLayerReindex()`
  on every render without worrying about performance.
- **Requires all children to re-render** — The repositioning algorithm walks
  the cursor chain through rendering children in document order. If some
  children are wrapped in `React.memo` and skip re-render, the cursor chain
  breaks and repositioning is silently incorrect. Ensure all children in the
  reindexed subtree participate in the render pass.
- **Does not trigger a re-render** — `useLayerReindex` operates imperatively on
  the shared registry during the current render pass. It does not schedule a
  state update or force a second render. The component that calls it must
  already be rendering.
- **Not a substitute for correct key props** — React uses `key` to preserve
  component identity across reorders. Always provide stable, unique keys to
  layer components. `useLayerReindex` handles the native z-order; React keys
  handle the Fiber reconciliation.

## See also

- **[Layer Ordering](../advanced/layer-ordering.md)** — Full explanation of
  the z-order model, fragment infrastructure, and `SharedLayer`
- **[SharedLayer](../components/shared-layer.md)** — Collapse same-type
  children into shared native layer fragments
- **[MapContainer](../components/map-container.md)** — The root map view that
  hosts the layer registry
