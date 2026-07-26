# LayerPath

Renders polylines on the map using a **shared native layer** — many JS
`<LayerPath>` components collapse into a single native `VectorLayer`, giving
excellent performance for large path counts (50–1000+).

## When to use LayerPath vs LayerPathJts

| Aspect | LayerPath | LayerPathJts |
|---|---|---|
| Native backend | Shared `VectorLayer` | Dedicated `PathLayer` per component |
| Performance at scale | Excellent (1 GPU draw call) | Worse (1 native layer per path) |
| Render ordering | Known bug (TODO.md #0) | Correct |
| Great-circle arcs | Not supported | `addGreatCircle` |
| Douglas-Peucker | External `simplify` library | Built-in via `Style.generalization` |
| Best for | 50–1000+ paths, route networks | 1–30 paths, guaranteed z-order |

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `coordinates` | `Position[]` | — | Array of `[lng, lat, alt?]` tuples defining the polyline |
| `paint` | `PathPaint` | defaults | Stroke/fill/stipple styling |
| `responseInclude` | `PathResponseInclude` | — | Fields to include in gesture events |
| `onPress` | `(e: PathTriggerEvent) => void` | — | Tap on this path's geometry |
| `onLongPress` | `(e: PathTriggerEvent) => void` | — | Long press on this path's geometry |
| `onDoubleTap` | `(e: PathTriggerEvent) => void` | — | Double-tap on this path's geometry |
| `onCreate` | `(response: LayerPathResponse) => void` | — | Fires after the native drawable is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the native drawable is removed |
| `onChange` | `(response: LayerPathResponse) => void` | — | Fires when a prop change triggers recreation |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## PathPaint

```tsx
type PathPaint = {
  strokeColor?: string;       // hex color, e.g. '#FF0000'
  strokeWidth?: number;       // pixels
  fillColor?: string;
  fillAlpha?: number;         // 0–1
  stipple?: number;           // stipple pattern (0–255)
  stippleWidth?: number;
  stippleColor?: string;
  stipplePercent?: number;    // 0–100
  cap?: 'butt' | 'round' | 'square';
  join?: 'miter' | 'round' | 'bevel';
  outlineColor?: string;
  outlineWidth?: number;
};
```

## Gesture callbacks

Gesture events include the hit-tested position and the drawable's uuid:

```tsx
type PathTriggerEvent = NativeSyntheticEvent<{
  uuid: string;
  lng: number;
  lat: number;
  x: number;    // screen x
  y: number;    // screen y
}>;
```

## Example

```tsx
import { MapContainer, LayerPath } from 'react-native-mapsforge-vtm';

const route = [
  [13.405, 52.52],
  [13.410, 52.53],
  [13.420, 52.54],
];

const App = () => (
  <MapContainer center={[13.41, 52.53]} zoomLevel={13}>
    <LayerPath
      coordinates={route}
      paint={{ strokeColor: '#FF0000', strokeWidth: 4 }}
      onPress={(e) => console.log('Tapped path at', e.nativeEvent.lng, e.nativeEvent.lat)}
    />
  </MapContainer>
);
```

## Known bug

**Render ordering does not strictly follow React tree order** for shared-layer
components. See `TODO.md` item 0. If you need guaranteed z-order, use
`LayerPathJts` instead.

## See also

- **[LayerPathJts](./layer-path-jts.md)** — Dedicated per-path native layers
- **[LayerShape](./layer-shape.md)** — Geometric shape overlays
