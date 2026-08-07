# SharedLayer

A React Context wrapper that collapses multiple children of the **same layer
type** into shared native layer fragments. Under the hood, children whose type
matches the preceding sibling of the same type are assigned the same fragment
UUID, reducing the number of native `Layer` objects and GPU draw calls.

## When to use it

- **Many same-type layers** — e.g. 50 `<LayerPath>` children. Without
  `SharedLayer`, each is its own native drawable. With `SharedLayer`, they
  collapse into one native `VectorLayer`.
- **Scoped grouping** — Only layers inside the `SharedLayer` context are
  collapsed. Layers outside remain independent.

## When NOT to use it

- **Different layer types** — `SharedLayer` only collapses same-type
  children. A `<LayerPath>` and a `<LayerShape>` inside the same
  `SharedLayer` remain separate native layers.
- **Need per-layer z-order** — Shared-layer children share one z-order
  position. If individual z-order matters, use `LayerPathJts` instead of
  `LayerPath`.
- **Need per-layer gesture handling** — Gesture hit-testing in shared layers
  resolves to the correct drawable, but the event fires through the shared
  layer's `onGesture` callback rather than per-layer `onPress`/`onLongPress`.

## Props

| Prop | Type | Description |
|---|---|---|
| `children` | `ReactNode` | Children to collapse into shared fragments |

## How it works

`SharedLayer` sets `registry.sharedLayerActive = true` in `MapHandleContext`.
During the render pass, `useLayerOrder` checks this flag: when active,
same-type consecutive children share a fragment UUID. The native side sees
fewer `Layer` objects, each containing multiple drawables.

This is orthogonal to `ReindexScope`:
- **SharedLayer** manages fragment assignment (which native layer a JS
  component lands in)
- **ReindexScope** manages position ranges (where in the z-order those
  fragments appear)

A layer can be in neither, either, or both.

## Example

```tsx
import {
  MapContainer,
  SharedLayer,
  LayerPath,
  LayerBitmapTile,
} from 'react-native-mapsforge-vtm';

const routes = [
  [[13.40, 52.52], [13.41, 52.53]],
  [[13.42, 52.54], [13.43, 52.55]],
  // ... 50 more routes
];

const App = () => (
  <MapContainer center={[13.41, 52.53]} zoomLevel={13}>
    <LayerBitmapTile
      url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
      zoomMax={18}
    />
    {/* All LayerPath children share one native VectorLayer */}
    <SharedLayer>
      {routes.map((coords, i) => (
        <LayerPath
          key={i}
          coordinates={coords}
          paint={{ strokeColor: '#FF0000', strokeWidth: 3 }}
        />
      ))}
    </SharedLayer>
    {/* This LayerPath is outside SharedLayer — independent native layer */}
    <LayerPath
      coordinates={[[13.40, 52.50], [13.44, 52.56]]}
      paint={{ strokeColor: '#0000FF', strokeWidth: 5 }}
    />
  </MapContainer>
);
```

## See also

- **[Layer Ordering](../advanced/layer-ordering.md)** — Full z-order model
- **[ReindexScope](./reindex-scope.md)** — Signal layer reorder to the native stack
- **[Performance](../advanced/performance.md)** — Batch insertion and shared layers
