# LayerMapsforge

Renders offline vector maps from mapsforge V5 `.map` files. This is the
primary layer for displaying OpenStreetMap data without a network connection.

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `mapFile` | `"/${string}" \| "content://${string}"` | — | Path to a mapsforge V5 `.map` file |
| `renderTheme` | `"/${string}" \| "content://${string}" \| BuiltInTheme` | `'DEFAULT'` | Render theme — built-in name or path to custom theme XML |
| `renderStyle` | `string` | — | Initial render style from the theme's `<stylemenu>` |
| `renderOverlays` | `string[]` | — | Initial overlay sub-options |
| `hasBuildings` | `boolean` | `false` | Enable 3D building rendering |
| `hasLabels` | `boolean` | `false` | Show text labels on a separate layer |
| `enabledZoomMin` | `number` | `0` | Minimum zoom at which the layer is visible |
| `enabledZoomMax` | `number` | `22` | Maximum zoom at which the layer is visible |
| `onCreate` | `(response: LayerMapsforgeResponse) => void` | — | Fires after the native layer is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the native layer is removed |
| `onChange` | `(response: LayerMapsforgeResponse) => void` | — | Fires when a prop change triggers recreation |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

### Built-in themes

```tsx
'DEFAULT' | 'BIKER' | 'MOTORIDER' | 'NEWTRON' | 'OSMARENDER' | 'TRONRENDER'
```

For a custom theme, pass an absolute filesystem path or `content://` URI to a
render-theme XML file.

## How it works

`LayerMapsforge` creates three native layers kept contiguous via
`useLayerOrder`:

1. **Base tile layer** — renders map tiles from the `.map` file
2. **Building layer** (optional, `hasBuildings`) — 3D building extrusion
3. **Label layer** (optional, `hasLabels`) — text labels on top of buildings

Toggling `hasBuildings` or `hasLabels` mounts/unmounts the sub-layer.

## LayerMapsforgeResponse

The `onCreate` / `onChange` callbacks receive:

```tsx
type LayerMapsforgeResponse = {
  uuid: string;           // native layer identifier
  bbox?: Bbox;            // [west, south, east, north] — map file bounds
  center?: Position;      // [lng, lat, alt?] — map file center
  createdBy?: string;     // tool that created the .map file
  projectionName?: string;
  comment?: string;
  fileSize?: string;
  fileVersion?: number;
  mapDate?: string;
};
```

## Finding render styles

Use `useRenderStyleOptions(renderTheme)` to read available styles and
overlay options from a theme's `<stylemenu>` block:

```tsx
import { useRenderStyleOptions } from 'react-native-mapsforge-vtm';

const styles = await useRenderStyleOptions('OSMARENDER');
// styles: RenderStyleOption[] = [
//   { value: 'DEFAULT', label: 'Default', overlays: [...] },
//   { value: 'NIGHT', label: 'Night', overlays: [...] },
// ]
```

This parse is cached natively by file path + last-modified and only reads the
`<stylemenu>` block.

## Example

```tsx
import { MapContainer, LayerMapsforge } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
    <LayerMapsforge
      mapFile="/storage/emulated/0/maps/berlin.map"
      renderTheme="OSMARENDER"
      renderStyle="NIGHT"
      hasBuildings={true}
      hasLabels={true}
    />
  </MapContainer>
);
```

## Where to get `.map` files

- [openandromaps.org](https://www.openandromaps.org/en/downloads) — free
  mapsforge V5 vector maps with multiple render themes

## See also

- **[useRenderStyleOptions()](../hooks/use-render-style-options.md)** — Read theme style menu
- **[CanvasAdapterModule](../api/canvas-adapter-module.md)** — Global text/line/symbol scaling
