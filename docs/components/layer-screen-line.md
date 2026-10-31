# LayerScreenLine

A polyline whose geometry is rebuilt on the Java side **every rendered
frame** from the frame's own sync viewport (`ScreenLineRenderer`, a
`BucketRenderer` subclass running on the GL thread). Zero latency, zero
bridge crossings, rotation/bearing-correct.

This is the zero-lag sibling of `LayerPath` for lines that must stay
glued to the viewport while panning (e.g. a line connecting a marker to
the map center). `LayerPath`'s screen-anchored points recompile
asynchronously on a worker thread and trail by 1–2 frames;
`LayerScreenLine` can never lag the map.

Each component renders one dedicated native layer — use it for a handful
of lines, not thousands. Gestures (`onPress`/hit-testing) are not
supported yet.

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `coordinates` | `(GeoJsonPosition \| ScreenPointSugar)[]` | — | At least 2 points. Elements may be geographic tuples `[lng, lat, alt?]` or screen-anchored points (`['50%', '50%']`, `['100dp', '24dp']`, `{ x, y, unit }`) |
| `paint` | `ScreenLinePaint` | defaults | Stroke styling |
| `onCreate` | `(response: LineResponse) => void` | — | Fires after the native line is created |
| `onRemove` | `(response: LineResponse) => void` | — | Fires after the native line is removed |
| `onChange` | `(response: LineResponse) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## ScreenLinePaint

```tsx
type ScreenLinePaint = {
  strokeColor?: string;   // hex color, e.g. '#000000'
  strokeWidth?: number;   // dp, screen-fixed (not map-scaled)
  cap?: 'SQUARE' | 'ROUND' | 'BUTT';
};
```

## Example

```tsx
import { MapContainer, LayerScreenLine } from 'react-native-mapsforge-vtm';

<MapContainer center={[13.41, 52.53]} zoomLevel={13}>
  <LayerScreenLine
    coordinates={[
      [13.405, 52.52],
      ['50%', '50%'],
    ]}
    paint={{ strokeColor: '#000000', strokeWidth: 2, cap: 'ROUND' }}
  />
</MapContainer>
```

The line above connects the geographic point to the viewport center —
during pan/zoom/rotate both ends stay glued to the map, with the
screen-anchored end tracking the viewport exactly.

## Notes

- Screen-anchored points resolve against the frame's own sync viewport
  (`GLViewport.fromScreenPoint`), so bearing/tilt are applied correctly
  — no `ScreenAnchorRegistry`/map-event listener involved.
- The geometry is clamped to the engine's fixed-point coordinate range
  (`TileClipper`), so far-away geographic endpoints render correctly
  (clipped at the range edge) instead of overflowing.
- Idle maps render no frames → no per-frame work.
- Point count is validated natively (≥ 2; anchor indices must match the
  provided geo coordinates, mirroring `LayerPath`'s `screenPoints`
  contract).
