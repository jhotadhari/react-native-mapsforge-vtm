# LayerBitmapTile

Renders online raster tiles from a tile-server URL template (OpenStreetMap,
custom tile servers, etc.).

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `url` | `string` | — | Tile URL template with `{Z}`/`{X}`/`{Y}` placeholders |
| `alpha` | `number` | `1` | Layer opacity, `0` (transparent) to `1` (opaque) |
| `zoomMin` | `number` | `0` | Minimum zoom at which tiles are requested |
| `zoomMax` | `number` | `22` | Maximum zoom at which tiles are requested |
| `cacheEnabled` | `boolean` | `true` | Enable HTTP caching |

## URL template

`{Z}`, `{X}`, and `{Y}` are replaced at runtime with the current zoom level
and tile coordinates:

```tsx
<LayerBitmapTile
  url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
  zoomMax={18}
/>
```

Any tile URL following the Slippy Map convention works. Subdomain rotation
(`{s}.tile.example.com`) is not built in — use a single URL or randomize it in
your own wrapper.

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

## See also

- **[LayerMBTilesBitmap](./layer-mbtiles-bitmap.md)** — Offline raster tiles from MBTiles
- **[LayerMapsforge](./layer-mapsforge.md)** — Offline vector maps
