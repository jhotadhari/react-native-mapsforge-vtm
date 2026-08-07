# Performance

Best practices for getting smooth map rendering at scale.

## Layer count guidance

| Component | Target count | Notes |
|---|---|---|
| `LayerPath` | 50–1000+ | Shared native layer — excellent at scale |
| `LayerPathJts` | 1–30 | Dedicated native layer per path |
| `Marker` | 1–500+ | Batch insertion via `MarkerBatchQueue` |
| `LayerShape` | 1–100 | One native layer per shape |
| `LayerMapsforge` | 1–3 | Each `.map` file is heavy |

## Use SharedLayer for same-type grouping

Wrap many same-type children in `<SharedLayer>` to collapse them into shared
native fragments:

```tsx
<SharedLayer>
  {routes.map((r) => (
    <LayerPath key={r.id} coordinates={r.coords} paint={r.paint} />
  ))}
</SharedLayer>
```

Without `SharedLayer`: N native layers + N draw calls.
With `SharedLayer`: 1 native layer + N drawables + 1 draw call.

## Use LayerPath for bulk rendering

`LayerPath` uses a shared native `VectorLayer` — 1000 paths = 1 GPU draw
call. `LayerPathJts` creates one native `PathLayer` per component — 30
paths = 30 draw calls. Choose the right one for your use case.

## Marker batch insertion

Markers are created via `MarkerBatchQueue`, which coalesces multiple
`Marker` mounts into a single native bridge call. N markers don't mean N
round trips — they're batched automatically.

## Avoid unnecessary layer recreations

Most layers recreate (remove then create) when a prop that's baked into
native construction changes. For `LayerMapsforge`, `mapFile` and
`renderTheme` trigger recreation; `enabledZoomMin`/`enabledZoomMax` update
in place. Check each layer's docs for which props cause a
teardown-and-recreate cycle.

## Map position consumption patterns

There are three tiers for reading the current map position, ordered from
simplest to most performant:

| Tier | API | Bridge crossings | React re-renders | Best for |
|---|---|---|---|---|
| Callback | `MapContainer.onMapUpdate` | ~25/sec (one-way) | ~25/sec | Coordinate tracking, debug overlays, one-shot reactions. Elevation is included in the `center` array (third element) from the DEM cache when available — `null` on cache miss. |
| Shared values | `useMapPosition()` from `reanimated` module | ~25/sec (writes only) | 0 | Smooth UI tracking at 60fps |
| Shared values (native) | `useMapPosition()` + `activateNativeBridge()` | **0** (writes bypass the bridge entirely) | 0 | Zero-jitter overlay tracking at true 60fps |
| Imperative | `useMap().getPosition()` | 2 per call (round-trip) | 0–1 | Button-triggered snapshots |

`useMapPosition()` returns reanimated shared values. Worklet consumers read
from them on the UI thread — zero bridge crossings, zero React re-renders
for reads. Requires `react-native-reanimated >= 3.0.0`.

### Native shared-value bridge (zero bridge crossings)

When `activateNativeBridge(handle)` is called, position data flows directly
from the vtm render thread into reanimated primitives — the JS bridge is
bypassed entirely. A worklet poller on the UI thread reads those primitives
each frame and updates standard `SharedValue` objects at true 60fps.

Use `setNativeNodeHandle` / `nativeNodeHandle` (already exposed by
`MapContainer`) to obtain the native view handle, then call
`activateNativeBridge` in a `useEffect`:

```tsx
import { useMapPosition } from 'react-native-mapsforge-vtm/reanimated';
import { useState, useEffect } from 'react';

function App() {
  const pos = useMapPosition();
  const [nativeNodeHandle, setNativeNodeHandle] =
    useState<number | null>(null);

  useEffect(() => {
    if (nativeNodeHandle) pos.activateNativeBridge(nativeNodeHandle);
  }, [nativeNodeHandle, pos]);

  return (
    <MapContainer
      nativeNodeHandle={nativeNodeHandle}
      setNativeNodeHandle={setNativeNodeHandle}
      onMapUpdate={pos.handleMapUpdate}
    >
      {/* layers */}
    </MapContainer>
  );
}
```

When the native bridge is inactive (no `activateNativeBridge` call, or
reanimated not installed), the hook falls back to the existing
`onMapUpdate` Fabric-event path — no change in behaviour.

### Altitude (elevation)

`useMap().getAltitudeAtPosition(lng, lat)` returns a `Promise<number | null>`
for point-elevation queries. It runs on the **Native Modules thread** (not
the render thread) — file I/O during HGT tile loads never blocks rendering.
Call it on a debounce after the map settles for center-altitude displays,
or directly for tap/long-press handlers.

## Touch latency

Avoid `pointerEvents="none"` on overlays that sit above the map. The map
view needs to receive touch events for pan/zoom. Use `pointerEvents="box-none"`
on overlays that partially cover the map.

## See also

- **[SharedLayer](../components/shared-layer.md)** — Fragment grouping
- **[LayerPath](../components/layer-path.md)** vs **[LayerPathJts](../components/layer-path-jts.md)** — Choosing the right path layer
