# LayerPathJts

Renders polylines using dedicated native `PathLayer` instances — one per JS
component. Uses vtm's JTS integration for advanced geometry features.

## When to use LayerPathJts vs LayerPath

| Aspect | LayerPathJts | LayerPath |
|---|---|---|
| Native backend | Dedicated `PathLayer` per component | Shared `VectorLayer` |
| Render ordering | Correct — per-component uuid | Known bug (TODO.md #0) |
| Great-circle arcs | `addGreatCircle` | Not supported |
| Douglas-Peucker | Built-in `Style.generalization` | External library |
| Performance at scale | Worse (1 native layer per path) | Excellent (1 GPU draw call) |
| Best for | 1–30 paths, guaranteed z-order | 50–1000+ paths |

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `lineString` | `Double[]` | — | JTS LineString as flattened `[lng, lat, lng, lat, …]` |
| `style` | `GeometryStyleJts` | defaults | Stroke/fill styling with built-in generalization |
| `responseInclude` | `PathJtsResponseInclude` | — | Fields to include in gesture events |
| `onPress` | `(e: PathJtsTriggerEvent) => void` | — | Tap on this path's geometry |
| `onLongPress` | `(e: PathJtsTriggerEvent) => void` | — | Long press on this path's geometry |
| `onDoubleTap` | `(e: PathJtsTriggerEvent) => void` | — | Double-tap on this path's geometry |
| `onCreate` | `(response: LayerPathJtsResponse) => void` | — | Fires after the native layer is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the native layer is removed |
| `onChange` | `(response: LayerPathJtsResponse) => void` | — | Fires when a prop change triggers recreation |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## GeometryStyleJts

Same fields as `GeometryStyle` plus built-in Douglas-Peucker generalization:

```tsx
type GeometryStyleJts = GeometryStyle & {
  generalization?: number;   // pixel distance tolerance (0 = disabled)
};
```

Set a small value (0.5–2) to reduce vertex count at low zoom levels
without visible quality loss.

## Great-circle arcs

Use `addGreatCircle` to draw the shortest path between two points on the
Earth's surface:

```tsx
// Handled internally — the native PathLayer computes the great-circle arc
// between consecutive points when the style enables it.
```

## Example

```tsx
import { MapContainer, LayerPathJts } from 'react-native-mapsforge-vtm';

// Flattened LineString: [lng, lat, lng, lat, …]
const lineString = [13.405, 52.52, 13.410, 52.53, 13.420, 52.54];

const App = () => (
  <MapContainer center={[13.41, 52.53]} zoomLevel={13}>
    <LayerPathJts
      lineString={lineString}
      style={{
        strokeColor: '#00AAFF',
        strokeWidth: 3,
        generalization: 1.0,
      }}
    />
  </MapContainer>
);
```

## See also

- **[LayerPath](./layer-path.md)** — Shared-layer paths for bulk rendering
- **[LayerShape](./layer-shape.md)** — Geometric shape overlays
