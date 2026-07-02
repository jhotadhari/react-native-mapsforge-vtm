# Quick Start

Render your first map in 5 minutes.

## Step 1: Import the components

```tsx
import {
  MapContainer,
  LayerBitmapTile,
  LayerScalebar,
} from 'react-native-mapsforge-vtm';
```

## Step 2: Add a MapContainer

```tsx
import { View } from 'react-native';

const App = () => (
  <View style={{ flex: 1 }}>
    <MapContainer
      center={[-77.6, -9.1]}   // [lng, lat] — GeoJSON Position tuple
      zoomLevel={12}
      width={400}
      height={600}
    >
      {/* Layers go here */}
    </MapContainer>
  </View>
);
```

`MapContainer` is the root — a Fabric-native view that hosts the vtm rendering
engine. Every other component must be a child of `MapContainer`.

### Center format: GeoJSON `Position`

All positional data uses GeoJSON `Position` tuples: `[lng, lat, alt?]`. This
replaces the old `{ lng, lat, alt? }` object format from the bridge-based API.
See the [Migration Guide](../../MIGRATION.md) for details.

### Width and height

- `width` defaults to the screen width (via `useWindowDimensions`)
- `height` should usually be specified explicitly — there's no auto-height
- Both accept device-independent pixels (dp)

## Step 3: Add a tile layer

The simplest background is an online raster tile layer:

```tsx
<MapContainer center={[-77.6, -9.1]} zoomLevel={12}>
  <LayerBitmapTile
    url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
    zoomMax={18}
  />
</MapContainer>
```

`{Z}`, `{X}`, `{Y}` in the URL are replaced at runtime with the current
zoom level and tile coordinates.

If you have offline `.map` files, use `LayerMapsforge` instead:

```tsx
<LayerMapsforge
  mapFile="/storage/emulated/0/maps/berlin.map"
  renderTheme="OSMARENDER"
/>
```

## Step 4: Add a scale bar

```tsx
<MapContainer center={[-77.6, -9.1]} zoomLevel={12}>
  <LayerBitmapTile
    url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
    zoomMax={18}
  />
  <LayerScalebar />
</MapContainer>
```

## Complete example

```tsx
import React from 'react';
import { View, useWindowDimensions } from 'react-native';
import {
  MapContainer,
  LayerBitmapTile,
  LayerScalebar,
  useMap,
} from 'react-native-mapsforge-vtm';

const MapControls = () => {
  const { zoomTo } = useMap();

  return (
    <Button
      title="Zoom to 15"
      onPress={() => zoomTo(15)}
    />
  );
};

const App = () => {
  const { width, height } = useWindowDimensions();

  return (
    <View style={{ flex: 1 }}>
      <MapContainer
        center={[13.405, 52.52]}   // Berlin
        zoomLevel={12}
        width={width}
        height={height}
      >
        <LayerBitmapTile
          url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
          zoomMax={18}
        />
        <LayerScalebar />

        {/* useMap() works inside MapContainer's children */}
        <MapControls />
      </MapContainer>
    </View>
  );
};
```

## Next steps

- **[MapContainer docs](../components/map-container.md)** — all props: zoom
  bounds, viewport constraints, interaction toggles, events
- **[useMap() docs](../hooks/use-map.md)** — imperative control:
  `panTo`, `flyTo`, `fitBounds`, altitude queries
- **[LayerMapsforge docs](../components/layer-mapsforge.md)** — offline vector
  maps with render themes and style overlays
- **[Example App](https://github.com/jhotadhari/react-native-mapsforge-vtm/tree/main/example)** —
  21 runnable examples covering every layer type and feature
