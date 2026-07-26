# LayerMarker / Marker

`LayerMarker` is a container that provides group-level defaults (symbol, color,
size) for `Marker` children. Each `Marker` is an individual map marker rendered
as a native drawable.

## LayerMarker

### Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `enabledZoomMin` | `number` | `0` | Minimum zoom at which markers are visible |
| `enabledZoomMax` | `number` | `22` | Maximum zoom at which markers are visible |
| `onCreate` | `(response: ResponseBase) => void` | — | Fires after the marker container is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the container is removed |
| `onChange` | `(response: ResponseBase) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

Group-level symbol defaults set on `LayerMarker` apply to all child `Marker`
instances that don't override them.

## Marker

### Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `position` | `Position` (`[lng, lat, alt?]`) | — | Marker position |
| `paint` | `MarkerPaint` | — | Marker appearance (color, size, shape) |
| `onPress` | `(e: MarkerEvent) => void` | — | Tap on this marker |
| `onLongPress` | `(e: MarkerEvent) => void` | — | Long press on this marker |
| `onDoubleTap` | `(e: MarkerEvent) => void` | — | Double-tap on this marker |
| `onCreate` | `(response: MarkerResponse) => void` | — | Fires after the marker drawable is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the marker is removed |
| `onChange` | `(response: MarkerResponse) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

### MarkerPaint

```tsx
type MarkerPaint = {
  type?: 'circle' | 'square' | 'diamond' | 'triangle' | 'star';
  size?: number;            // radius in dp
  fillColor?: string;       // hex color
  fillAlpha?: number;       // 0–1
  strokeColor?: string;
  strokeWidth?: number;
  text?: string;            // label text
  textColor?: string;
  textSize?: number;
  hotspot?: 'center' | 'bottom' | 'top' | 'left' | 'right';
};
```

### Batch creation

Markers are created in batches via `MarkerBatchQueue` — multiple `Marker`
mounts are coalesced into a single native bridge call. This avoids the N
bridge calls = N round trips problem when rendering many markers.

## Example

```tsx
import { MapContainer, LayerMarker, Marker } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
    <LayerMarker>
      <Marker
        position={[13.405, 52.52]}
        paint={{ type: 'circle', size: 12, fillColor: '#FF0000' }}
        onPress={(e) => console.log('Tapped marker at', e.nativeEvent.position)}
      />
      <Marker
        position={[13.410, 52.53]}
        paint={{ type: 'star', size: 16, fillColor: '#0000FF' }}
      />
    </LayerMarker>
  </MapContainer>
);
```

## See also

- **[LayerPath](./layer-path.md)** — Path overlays with gesture support
- **[CanvasAdapterModule](../api/canvas-adapter-module.md)** — Global symbol scaling
