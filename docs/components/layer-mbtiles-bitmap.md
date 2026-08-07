# LayerMBTilesBitmap

Renders offline raster tiles from MBTiles files (SQLite databases containing
pre-rendered map tiles).

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `mbtilesFile` | `"/${string}" \| "content://${string}"` | — | Path to an MBTiles file |
| `transparentColor` | `string` | — | Hex color to treat as transparent (e.g. `'#FFFFFF'`) |
| `enabledZoomMin` | `number` | `0` | Minimum zoom at which tiles are visible |
| `enabledZoomMax` | `number` | `22` | Maximum zoom at which tiles are visible |
| `onCreate` | `(response: LayerMBTilesBitmapResponse) => void` | — | Fires after the layer is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the layer is removed |
| `onChange` | `(response: LayerMBTilesBitmapResponse) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## Example

```tsx
import { MapContainer, LayerMBTilesBitmap } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
    <LayerMBTilesBitmap
      mbtilesFile="/storage/emulated/0/maps/overview.mbtiles"
    />
  </MapContainer>
);
```

## Where to get MBTiles

- [openandromaps.org](https://www.openandromaps.org/en/downloads/general-maps) —
  free raster overview maps in MBTiles format

## See also

- **[LayerBitmapTile](./layer-bitmap-tile.md)** — Online raster tiles
- **[LayerMapsforge](./layer-mapsforge.md)** — Offline vector maps
