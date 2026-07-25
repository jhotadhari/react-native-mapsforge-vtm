# LayerHillshading

Renders shaded-relief tiles from digital elevation model (DEM) data in HGT
format. Uses vtm's `HillshadingTileSource` and configurable shading algorithms.

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `hgtDirPath` | `"/${string}" \| "content://${string}"` | — | Directory containing `.hgt` files |
| `algorithm` | `ShadingAlgorithm` | `'simple'` | Shading algorithm type |
| `algorithmOptions` | `ShadingAlgorithmOptions` | defaults | Algorithm-specific parameters |
| `enabledZoomMin` | `number` | `0` | Minimum zoom at which shading is visible |
| `enabledZoomMax` | `number` | `22` | Maximum zoom at which shading is visible |
| `onCreate` | `(response: ResponseBase) => void` | — | Fires after the native layer is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the layer is removed |
| `onChange` | `(response: ResponseBase) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## Test data

The example app and development workflow use SRTM3 data from **SE19.zip**
(southern Peru / western Bolivia, lat [-21, -17], lng [-72, -67]).
Download from [viewfinderpanoramas.org](https://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm)
and extract the `.hgt` files to `/sdcard/Download/test-data/hgt` on the device.

## Shading algorithms

Available algorithms (from vtm's `ShadingAlgorithm`):

```tsx
type ShadingAlgorithm =
  | 'simple'
  | 'simple_combined'
  | 'simple_with_slope'
  | 'simple_with_slope_combined'
  | 'color_relief'
  | 'color_relief_combined'
  | 'color_relief_with_slope_combined';
```

Each algorithm takes options like `lightAzimuth`, `lightElevation`,
`slopeFactor`, `exaggeration` — see `ShadingAlgorithmOptions` in the
library's TypeScript types.

## HGT data

HGT files follow the SRTM naming convention: `N52E013.hgt`. The layer scans
`hgtDirPath` for matching files and reads elevation data as needed.

You can get HGT files at 3 arc-second resolution from
[viewfinderpanoramas.org](https://viewfinderpanoramas.org/dem3.html).

## Note: separate from MapContainer's hgtDirPath

`LayerHillshading`'s `hgtDirPath` is **independent** of `MapContainer`'s
`hgtDirPath`. The former drives visual shading; the latter enables point
altitude queries via `useMap().getAltitudeAtPosition()`. If you need both,
set both props.

## Example

```tsx
import { MapContainer, LayerMapsforge, LayerHillshading } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
    <LayerMapsforge
      mapFile="/storage/emulated/0/maps/berlin.map"
      renderTheme="OSMARENDER"
    />
    <LayerHillshading
      hgtDirPath="/storage/emulated/0/hgt/"
      algorithm="simple_combined"
    />
  </MapContainer>
);
```

## See also

- **[LayerMapsforge](./layer-mapsforge.md)** — Vector map layer (usually paired with hillshading)
- **[MapContainer](./map-container.md)** — `hgtDirPath` for point altitude queries
