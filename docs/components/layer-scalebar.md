# LayerScalebar

Displays a map scale bar that updates automatically as the user zooms and pans.

This component has **no configurable props** — it renders a standard metric
scale bar using vtm's built-in scale bar layer.

## Props

None. The scale bar is a simple, zero-configuration overlay.

## Example

```tsx
import { MapContainer, LayerBitmapTile, LayerScalebar } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
    <LayerBitmapTile
      url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
      zoomMax={18}
    />
    <LayerScalebar />
  </MapContainer>
);
```

The scale bar renders in the bottom-left corner of the map and updates as the
user pans or zooms. No styling or positioning props are available — it uses
vtm's default scale bar appearance.

## See also

- **[MapContainer](./map-container.md)** — Root map view
- **[LayerBitmapTile](./layer-bitmap-tile.md)** — Commonly paired with online tiles
