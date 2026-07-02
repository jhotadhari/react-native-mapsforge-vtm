# CanvasAdapterModule

Global text, line, and symbol scale configuration for the vtm rendering engine.
Changes to these values affect all map views in the process.

```tsx
import { CanvasAdapterModule } from 'react-native-mapsforge-vtm';
```

## API

```tsx
// Set the global text scale factor
CanvasAdapterModule.setTextScale(1.5);

// Set the global line scale factor
CanvasAdapterModule.setLineScale(2.0);

// Set the global symbol scale factor
CanvasAdapterModule.setSymbolScale(1.2);
```

All methods are synchronous — they call into vtm's static `CanvasAdapter`
fields directly. No `nativeNodeHandle` is needed.

## What these control

| Scale | Affects |
|---|---|
| `textScale` | Font size of labels on `LayerMapsforge` (when labels are enabled) |
| `lineScale` | Width of roads, paths, and other line features |
| `symbolScale` | Size of map symbols (POI icons, etc.) |

## Use cases

- **Accessibility** — Increase `textScale` and `symbolScale` for visually
  impaired users
- **High-density displays** — Adjust `lineScale` for devices where default
  line widths appear too thin
- **Theming** — Scale map elements globally without changing the render theme

## Example

```tsx
import { useEffect } from 'react';
import { MapContainer, LayerMapsforge, CanvasAdapterModule } from 'react-native-mapsforge-vtm';
import { useWindowDimensions, PixelRatio } from 'react-native';

const App = () => {
  const { width } = useWindowDimensions();
  const pixelRatio = PixelRatio.get();

  useEffect(() => {
    // Adjust line widths for high-DPI screens
    if (pixelRatio >= 3) {
      CanvasAdapterModule.setLineScale(1.5);
    }
  }, [pixelRatio]);

  return (
    <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
      <LayerMapsforge
        mapFile="/storage/emulated/0/maps/berlin.map"
        renderTheme="OSMARENDER"
        hasLabels={true}
      />
    </MapContainer>
  );
};
```

## See also

- **[LayerMapsforge](../components/layer-mapsforge.md)** — Vector maps with labels and symbols
- **[LayerMarker](../components/layer-marker.md)** — Custom markers (unaffected by symbolScale)
