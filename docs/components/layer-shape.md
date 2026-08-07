# LayerShape

Draws geometric shapes on the map using vtm's JTS drawable infrastructure.
Each shape is a dedicated native `VectorLayer`.

## Shape types

| Type | Key | Geometry |
|---|---|---|
| Polygon | `polygon` | `Position[][]` — outer ring + optional hole rings |
| Circle | `circle` | Center `Position` + `radiusKm` |
| Rectangle | `rectangle` | Two corner `Position`s |
| Hexagon | `hexagon` | Center `Position` + `radiusKm` |
| Point | `point` | Single `Position` |

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `shape` | `ShapeDefinition` | — | Shape type + geometry |
| `paint` | `ShapePaint` | defaults | Fill/stroke styling |
| `onPress` | `(e: ShapeTriggerEvent) => void` | — | Tap on this shape |
| `onLongPress` | `(e: ShapeTriggerEvent) => void` | — | Long press on this shape |
| `onDoubleTap` | `(e: ShapeTriggerEvent) => void` | — | Double-tap on this shape |
| `onCreate` | `(response: LayerShapeResponse) => void` | — | Fires after the native layer is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the layer is removed |
| `onChange` | `(response: LayerShapeResponse) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## ShapeDefinition

```tsx
type ShapeDefinition =
  | { type: 'polygon'; rings: Position[][] }          // rings[0] = outer, rest = holes
  | { type: 'circle'; center: Position; radiusKm: number }
  | { type: 'rectangle'; corner1: Position; corner2: Position }
  | { type: 'hexagon'; center: Position; radiusKm: number }
  | { type: 'point'; position: Position };
```

## ShapePaint

```tsx
type ShapePaint = {
  fillColor?: string;          // hex color
  fillAlpha?: number;          // 0–1
  strokeColor?: string;
  strokeWidth?: number;        // pixels
  stipple?: number;            // 0–255 pattern
  stippleWidth?: number;
  stippleColor?: string;
  stipplePercent?: number;
  cap?: 'butt' | 'round' | 'square';
  join?: 'miter' | 'round' | 'bevel';
};
```

## Example

```tsx
import { MapContainer, LayerShape, LayerBitmapTile } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
    <LayerBitmapTile
      url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
      zoomMax={18}
    />

    {/* A semi-transparent red circle */}
    <LayerShape
      shape={{ type: 'circle', center: [13.405, 52.52], radiusKm: 2 }}
      paint={{ fillColor: '#FF0000', fillAlpha: 0.3, strokeColor: '#CC0000', strokeWidth: 2 }}
    />

    {/* A rectangle bounding box */}
    <LayerShape
      shape={{ type: 'rectangle', corner1: [13.38, 52.50], corner2: [13.43, 52.55] }}
      paint={{ fillColor: '#0000FF', fillAlpha: 0.1, strokeColor: '#0000FF', strokeWidth: 1 }}
    />
  </MapContainer>
);
```

## See also

- **[LayerPath](./layer-path.md)** — Polyline overlays
- **[LayerPathJts](./layer-path-jts.md)** — JTS-based path overlays
