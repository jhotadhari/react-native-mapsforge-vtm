# useMap()

Imperative map control hook. Provides methods to pan, zoom, animate, fit
bounds, and query the map's current position — all backed by native
TurboModule calls that dispatch to the UI thread.

```tsx
import { useMap } from 'react-native-mapsforge-vtm';
```

## Basic usage

```tsx
const MyControl = () => {
  const { panTo, flyTo, getPosition } = useMap();

  const goToBerlin = () => panTo([13.405, 52.52]);
  const goToParis = () => flyTo({ center: [2.349, 48.853] });
  const whereAreWe = async () => {
    const pos = await getPosition();
    console.log(pos.center);  // [lng, lat, alt?]
  };

  return (/* ... */);
};
```

Call it from any component nested inside `<MapContainer>`.

## Calling from outside the map tree

Components rendered **outside** `MapContainer`'s children (e.g. a sibling
toolbar) don't have access to `MapHandleContext`. Use the state-lifting
pattern:

```tsx
const App = () => {
  const [handle, setHandle] = useState<number | null>(null);

  return (
    <>
      <MapContainer nativeNodeHandle={handle} setNativeNodeHandle={setHandle}>
        {/* layers */}
      </MapContainer>
      <Toolbar mapHandle={handle} />
    </>
  );
};

const Toolbar = ({ mapHandle }: { mapHandle: number | null }) => {
  const { zoomTo } = useMap(mapHandle);
  return <Button title="Zoom to 10" onPress={() => zoomTo(10)} />;
};
```

## Methods

All methods return `Promise<void>` (or `Promise<…>` for queries). Await them
to chain operations or catch errors.

### Position queries

#### `getPosition(): Promise<GetPositionResponse>`

Returns the current map position (center, zoomLevel, bearing, tilt, roll, scale).

### Camera control

#### `jumpTo(target: MapPositionTarget): Promise<void>`

Instant jump (0ms duration `animateTo`).

#### `panTo(center: Position): Promise<void>`

Pan to a new center, keeping current zoom/tilt/bearing.

#### `panBy(deltaLngLat: [number, number]): Promise<void>`

Pan by a delta in lng/lat degrees. Reads current position first, then pans.

#### `setZoom(zoomLevel: number): Promise<void>` / `zoomTo(zoomLevel: number): Promise<void>`

Set the zoom level (both names do the same thing).

#### `zoomOut(by?: number): Promise<void>`

Zoom out by `by` levels (default 1). Reads current zoom, subtracts, then sets.

#### `setBearing(bearing: number): Promise<void>` / `rotateTo(bearing: number): Promise<void>`

Set the map rotation in degrees clockwise from north.

#### `resetNorth(): Promise<void>`

Set bearing to 0°.

#### `resetNorthPitch(): Promise<void>`

Set bearing to 0° and tilt to 0°.

#### `setRoll(roll: number): Promise<void>`

Set the map roll in degrees.

### Animated camera

#### `animateTo(target: MapPositionTarget, options?: AnimationOptions): Promise<void>`

Generic animate. Fields set on `target` change; omitted fields stay as-is.

```tsx
await animateTo(
  { center: [13.405, 52.52], zoomLevel: 14 },
  { duration: 2000, easing: 'sine_inout' }
);
```

#### `easeTo(target: MapPositionTarget, options?: AnimationOptions): Promise<void>`

Gentle animation. Defaults: 300ms, `sine_inout` easing.

#### `flyTo(target: MapPositionTarget, options?: AnimationOptions): Promise<void>`

Dramatic animation. Defaults: 1200ms, `expo_out` easing.

### Bounds fitting

#### `fitBounds(bounds: Bbox, options?: FitBoundsOptions): Promise<void>` / `setBounds(bounds, options?): Promise<void>`

Animate so the given bounding box is visible. `bounds` is a GeoJSON bbox:
`[west, south, east, north]` (4 numbers).

```tsx
await fitBounds([-0.5, 51.3, 0.5, 51.7], { paddingPx: 50 });
```

#### `flyToBounds(bounds: Bbox, options?: FitBoundsOptions): Promise<void>`

Like `fitBounds` but with `flyTo` defaults (1200ms, `expo_out`).

#### `panInsideBounds(bounds: Bbox): Promise<void>`

Clamp the current center into the given bounds via per-axis clamping.

#### `panInside(point: Position): Promise<void>`

Pan to a point (approximate, same caveat as `panInsideBounds`).

### Altitude queries

#### `getAltitudeAtPosition(lng: number, lat: number): Promise<number | null>`

Returns elevation in metres at a geographic coordinate, or `null` if no HGT
data covers that position.

Requires `hgtDirPath` to be set on the `MapContainer`. This queries the
MapContainer's own elevation data source — independent of `LayerHillshading`'s
HGT data.

```tsx
const elevation = await getAltitudeAtPosition(13.405, 52.52);
console.log(elevation); // 34 or null
```

## Types

### `MapPositionTarget`

```tsx
type MapPositionTarget = {
  center?: Position;     // [lng, lat, alt?]
  zoomLevel?: number;
  bearing?: number;
  tilt?: number;
  roll?: number;
};
```

Omitted fields are left unchanged.

### `AnimationOptions`

```tsx
type AnimationOptions = {
  duration?: number;     // ms, default varies by method
  easing?: EasingType;   // default varies by method
};

type EasingType =
  | 'linear' | 'sine_inout' | 'sine_in' | 'sine_out'
  | 'expo_out' | 'quad_inout' | 'cubic_inout'
  | 'quart_inout' | 'quint_inout';
```

### `FitBoundsOptions`

```tsx
type FitBoundsOptions = AnimationOptions & {
  paddingPx?: number;
};
```

### `GetPositionResponse`

```tsx
type GetPositionResponse = {
  center: Position;
  zoomLevel: number;
  zoom: number;
  scale: number;
  zoomScale: number;
  bearing: number;
  tilt: number;
  roll: number;
};
```

## Error handling

`requireHandle()` throws synchronously if the map hasn't mounted yet. Guard
button-press call sites:

```tsx
const handle = useRef<number | null>(null);
<Button onPress={() => { if (handle.current) zoomTo(15); }} />
```

### `getDebugLayerDump(): Promise<DebugLayerDump>`

One-shot async JSON dump of every layer on the map — native ground truth
(actual vtm `Layer` objects, their Java class names, z-indices, enabled
state) plus the JS-side component registry (React render order, fragment
assignments, generation counter).

```tsx
const { getDebugLayerDump } = useMap();
const dump = await getDebugLayerDump();
console.log(JSON.stringify(dump, null, 2));
```

See **[getDebugLayerDump()](../debug/get-debug-layer-dump.md)** for the
full return-type reference and interpretation guide.

## See also

- **[MapContainer](../components/map-container.md)** — Root map view
- **[Performance](../advanced/performance.md)** — Reanimated `useMapPosition()` for 60fps tracking
- **[Debug tools](../debug/get-debug-layer-dump.md)** — Layer introspection and debugging
