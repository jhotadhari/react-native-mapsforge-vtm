# LayerDebugTree

A visual debug overlay that renders the live layer tree as a collapsible,
color-coded list. Uses `useLayerDebugInfo()` internally.

```tsx
import { LayerDebugTree } from 'react-native-mapsforge-vtm';
```

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `maxHeight` | `number` | `300` | Maximum height of the overlay in dp |

## Features

- **Color-coded type badges** — each layer type (`LayerPath`, `LayerMapsforge`,
  `LayerMarker`, etc.) gets a distinct color
- **Fragment grouping** — layers sharing a fragment UUID are visually grouped
- **Collapsible sections** — tap a fragment group to collapse/expand
- **Live updates** — re-renders automatically as layers are created, removed,
  or reordered

## Usage

```tsx
import { MapContainer, LayerDebugTree } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
    <LayerBitmapTile
      url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
      zoomMax={18}
    />
    {/* ...your layers... */}

    {/* Debug overlay — renders last so it's on top */}
    <LayerDebugTree maxHeight={250} />
  </MapContainer>
);
```

Remove or comment out `<LayerDebugTree />` in production — it subscribes to
the layer registry and re-renders on every change.

## See also

- **[useLayerDebugInfo](./use-layer-debug-info.md)** — Raw data hook
- **[Layer Ordering](../advanced/layer-ordering.md)** — Understanding z-order
