# MapContainer

The root Fabric view that hosts the vtm map rendering engine. Every other
component (`LayerMapsforge`, `LayerPath`, `LayerMarker`, etc.) must be a
child of `MapContainer`.

## Props

### Position & viewport

| Prop | Type | Default | Description |
|---|---|---|---|
| `center` | `Position` (`[lng, lat, alt?]`) | `[0, 0]` | Initial map center |
| `zoomLevel` | `number` | `1` | Initial zoom level (integer) |
| `width` | `number` | screen width | Map width in dp |
| `height` | `number` | `1` | Map height in dp |

### Zoom bounds

| Prop | Type | Default | Description |
|---|---|---|---|
| `zoomMin` | `number` | `0` | Minimum allowed zoom level |
| `zoomMax` | `number` | `22` | Maximum allowed zoom level |

### Interaction toggles

| Prop | Type | Default | Description |
|---|---|---|---|
| `moveEnabled` | `boolean` | `true` | Allow panning |
| `tiltEnabled` | `boolean` | `true` | Allow tilt (two-finger vertical drag) |
| `rotationEnabled` | `boolean` | `true` | Allow rotation (two-finger twist) |
| `zoomEnabled` | `boolean` | `true` | Allow pinch-zoom |

### Viewport values & bounds

Set initial tilt, bearing, or roll values, and constrain their ranges:

| Prop | Type | Default | Range |
|---|---|---|---|
| `tilt` / `minTilt` / `maxTilt` | `number` | `0` / `0` / `65` | degrees from vertical |
| `bearing` / `minBearing` / `maxBearing` | `number` | `0` / `-360` / `360` | degrees clockwise from north |
| `roll` / `minRoll` / `maxRoll` | `number` | `0` / `-30` / `30` | degrees of roll |

### Events

Events are direct Fabric view props — your handler receives a
`NativeSyntheticEvent<T>`. Read fields from `event.nativeEvent`, not the
event itself.

| Prop | Type | Description |
|---|---|---|
| `onMapUpdate` | `(e: NativeSyntheticEvent<MapEventResponse>) => void` | Map position changed (pan, zoom, tilt, etc.). Rate-limited by `mapUpdateInterval`. |
| `onPause` | `(e: NativeSyntheticEvent<MapEventResponse>) => void` | Map paused (activity lifecycle) |
| `onResume` | `(e: NativeSyntheticEvent<MapEventResponse>) => void` | Map resumed |
| `onError` | `(e: NativeSyntheticEvent<{ errorMsg: string }>) => void` | Native error occurred |
| `onTap` | `(e: NativeSyntheticEvent<TapEventResponse>) => void` | Single tap on the map (not on a marker/path) |
| `onLongPress` | `(e: NativeSyntheticEvent<LongPressEventResponse>) => void` | Long press on the map |
| `onMapCreated` | `() => void` | Internal — fires when the native `MapView` is ready |

### Map update configuration

| Prop | Type | Default | Description |
|---|---|---|---|
| `responseInclude` | `ResponseInclude` | (all level 1) | Which fields to include in events. Level 1 = `onPause`/`onResume`, level 2 = `onMapUpdate`. Each field is an integer threshold. |
| `mapUpdateInterval` | `number` | `40` | Minimum ms between `onMapUpdate` events (throttle window) |
| `emitsMapUpdateEvents` | `boolean` | `true` if `onMapUpdate` set | Enable/disable position events |

### State lifting

| Prop | Type | Description |
|---|---|---|
| `nativeNodeHandle` | `number \| null` | Native view handle — lift up for use outside the map tree |
| `setNativeNodeHandle` | `Dispatch<SetStateAction<number \| null>>` | State setter for the handle |

These let components **outside** `MapContainer`'s children call
`useMap(nativeNodeHandle)`:

```tsx
const [handle, setHandle] = useState<number | null>(null);

return (
  <>
    <MapContainer nativeNodeHandle={handle} setNativeNodeHandle={setHandle}>
      {/* layers */}
    </MapContainer>
    <Toolbar mapHandle={handle} />   {/* calls useMap(handle) */}
  </>
);
```

### Elevation / HGT

| Prop | Type | Default | Description |
|---|---|---|---|
| `hgtDirPath` | `string \| null` | `null` | Path to HGT directory for altitude queries via `useMap().getAltitudeAtPosition()` |

This is separate from `LayerHillshading`'s `hgtDirPath`.

## Example

```tsx
import { MapContainer, LayerBitmapTile } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer
    center={[13.405, 52.52]}
    zoomLevel={12}
    zoomMin={2}
    zoomMax={18}
    width={400}
    height={600}
    tiltEnabled={false}
    onTap={(e) => {
      const { lng, lat } = e.nativeEvent;
      console.log(`Tapped at ${lng.toFixed(4)}, ${lat.toFixed(4)}`);
    }}
  >
    <LayerBitmapTile
      url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
      zoomMax={18}
    />
  </MapContainer>
);
```

## See also

- **[useMap()](../hooks/use-map.md)** — Imperative map control
- **[Layer Ordering](../advanced/layer-ordering.md)** — How z-order works
- **[Quick Start](../getting-started/quick-start.md)** — First-map walkthrough
