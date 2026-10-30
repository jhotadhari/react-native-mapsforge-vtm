# LayerPath

Renders polylines on the map using a **shared native layer** — many JS
`<LayerPath>` components collapse into a single native `VectorLayer`, giving
excellent performance for large path counts (50–1000+).

## When to use LayerPath vs LayerPathJts

| Aspect | LayerPath | LayerPathJts |
|---|---|---|
| Native backend | Shared `VectorLayer` | Dedicated `PathLayer` per component |
| Performance at scale | Excellent (1 GPU draw call) | Worse (1 native layer per path) |
| Render ordering | Should work correctly, sometimes doesn't | Correct |
| Great-circle arcs | Not supported | `addGreatCircle` |
| Douglas-Peucker | External `simplify` library | Built-in via `Style.generalization` |
| Best for | 50–1000+ paths, route networks | 1–30 paths, guaranteed z-order |

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `coordinates` | `(GeoJsonPosition \| ScreenPointSugar)[]` | — | Array of `[lng, lat, alt?]` tuples — or screen-anchored points (`['50%', '50%']`, `['100dp', '24dp']`, `{ x, y, unit }`) — defining the polyline |
| `paint` | `PathPaint` | defaults | Stroke/fill/stipple styling |
| `responseInclude` | `PathResponseInclude` | — | Fields to include in gesture events |
| `onPress` | `(e: PathTriggerEvent) => void` | — | Tap on this path's geometry |
| `onLongPress` | `(e: PathTriggerEvent) => void` | — | Long press on this path's geometry |
| `onDoubleTap` | `(e: PathTriggerEvent) => void` | — | Double-tap on this path's geometry |
| `onCreate` | `(response: LayerPathResponse) => void` | — | Fires after the native drawable is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the native drawable is removed |
| `onChange` | `(response: LayerPathResponse) => void` | — | Fires when a prop change triggers recreation |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## Screen-anchored points

Any element of `coordinates` may be a **screen-anchored point** instead of a
geographic tuple. The Java side re-resolves the geographic coordinate for
anchored points on **every map movement** (per frame, on the map event
thread) — no JS round trips — so the point stays glued to its screen position
during pan, zoom, and rotate. This is the escape hatch for geometry that must
connect a native layer to a viewport-relative position (e.g. a line from a
marker to the map center).

| Form | Meaning |
|---|---|
| `['50%', '50%']` | 50% of viewport width/height (top-left origin) |
| `['100dp', '24dp']` | 100 × 24 dp from the top-left |
| `{ x: 0.5, y: 0.5 }` | numeric fraction form (0–1, `unit` defaults to `'fraction'`) |
| `{ x: 120, y: 24, unit: 'dp' }` | numeric dp form |

Notes:

- The string sugar is normalized in JS; only numeric `screenPoints` cross the
  bridge.
- Anchored points keep their JSX order — `coordinates={[[lng, lat], ['50%', '50%']]}`
  draws from the geographic point to the viewport center.
- Anchored entries are excluded from the append-only fast path (their stored
  coordinates mutate per frame) and get a world-envelope hit-test index.
- The per-frame updater binds only while anchored entries exist; maps without
  anchored geometry pay nothing.

```tsx
<LayerPath
	coordinates={[[13.405, 52.52], ['50%', '50%']]}
	paint={{ strokeColor: '#000000', strokeWidth: 2, fixed: true }}
/>
```

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

`PathPaint` also supports a repeating line texture: `textureFilePath` (PNG from Android assets) with `stipple` as the repeat period in px, `stippleColor` as tint, `transparent` to blend gaps over the map, and `fixed` to keep it zoom-stable.

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

## See also

- **[LayerPathJts](./layer-path-jts.md)** — Dedicated per-path native layers
- **[LayerShape](./layer-shape.md)** — Geometric shape overlays
