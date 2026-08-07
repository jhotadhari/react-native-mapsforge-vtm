# createMapHandle() / createMapHandleRegistry()

Non-React factories for the imperative map-control + elevation API.
`createMapHandle` builds the full API object from a concrete
`nativeNodeHandle`; `createMapHandleRegistry` provides a singleton
`wire`/`unwire` pattern so non-React code (Redux thunks, services,
background tasks) can access it without a component tree.

```tsx
import {
  createMapHandle,
  createMapHandleRegistry,
} from 'react-native-mapsforge-vtm';
```

## Why two APIs?

`useMap()` is the React hook — it reads `nativeNodeHandle` from
`MapHandleContext` and adds `getDebugLayerDump()` (which needs the JS-side
`LayerOrderRegistry`). `createMapHandle()` is the pure factory — no React
dependency, callable from anywhere. `createMapHandleRegistry()` is the
singleton glue that lets React wire the handle and non-React code consume it.

## createMapHandle(nativeNodeHandle)

Creates the full imperative API from a resolved (non-null) view tag.

```tsx
import { createMapHandle } from 'react-native-mapsforge-vtm';

const handle = createMapHandle(viewTag);
await handle.flyTo({ center: [13.405, 52.52], zoomLevel: 14 });
```

### Camera

| Method | Signature | Description |
|---|---|---|
| `getPosition` | `() => Promise<GetPositionResponse>` | Current map position (center, zoom, bearing, tilt, roll, scale) |
| `jumpTo` | `(target: MapPositionTarget) => Promise<void>` | Instant jump (0ms animateTo) |
| `panTo` | `(center: Position) => Promise<void>` | Pan to center, keeping current zoom/tilt/bearing |
| `panBy` | `(deltaLngLat: [number, number]) => Promise<void>` | Pan by a lng/lat delta |
| `setZoom` / `zoomTo` | `(zoomLevel: number) => Promise<void>` | Set zoom level |
| `zoomOut` | `(by?: number) => Promise<void>` | Zoom out by `by` levels (default 1) |
| `setBearing` / `rotateTo` | `(bearing: number) => Promise<void>` | Set rotation (degrees clockwise from north) |
| `resetNorth` | `() => Promise<void>` | Set bearing to 0° |
| `resetNorthPitch` | `() => Promise<void>` | Set bearing to 0° and tilt to 0° |
| `setRoll` | `(roll: number) => Promise<void>` | Set roll in degrees |

### Animation

| Method | Signature | Defaults |
|---|---|---|
| `animateTo` | `(target: MapPositionTarget, options?: AnimationOptions) => Promise<void>` | 0ms, `linear` |
| `easeTo` | `(target: MapPositionTarget, options?: AnimationOptions) => Promise<void>` | 300ms, `sine_inout` |
| `flyTo` | `(target: MapPositionTarget, options?: AnimationOptions) => Promise<void>` | 1200ms, `expo_out` |

`MapPositionTarget` fields (`center`, `zoomLevel`, `bearing`, `tilt`, `roll`)
are all optional — omitted fields stay as-is.

### Bounds fitting

| Method | Signature | Description |
|---|---|---|
| `fitBounds` / `setBounds` | `(bounds: Bbox, options?: FitBoundsOptions) => Promise<void>` | Animate so bbox is visible |
| `flyToBounds` | `(bounds: Bbox, options?: FitBoundsOptions) => Promise<void>` | Like fitBounds with flyTo defaults |
| `panInsideBounds` | `(bounds: Bbox) => Promise<void>` | Clamp center into bounds (per-axis clamp) |
| `panInside` | `(point: Position) => Promise<void>` | Pan to point |

`Bbox` is `[west, south, east, north]` (GeoJSON order, 4 numbers).

### Elevation

| Method | Signature | Description |
|---|---|---|
| `getAltitudeAtPosition` | `(lng, lat) => Promise<number \| null>` | Bilinear-interpolated elevation in metres; triggers preload on cache miss; catches errors → `null` |
| `hasDataAtPosition` | `(lng, lat) => Promise<boolean>` | Fast check: HGT file exists? No I/O. Catches errors → `false` |
| `isTileCached` | `(lng, lat) => Promise<boolean>` | Direct LRU-cache check. Never triggers preload. Unambiguous vs. void pixels |
| `setCacheCapacity` | `(capacity: number) => Promise<void>` | Resize LRU cache. **Throws** on failure |
| `getAltitudeAtPositionRetry` | `(lng, lat, opts?) => Promise<number \| null>` | Exponential-backoff retry wrapper. Checks `hasDataAtPosition` first |

`getAltitudeAtPositionRetry` options: `{ maxRetries?: number }` (default 10).
Backoff: 10ms → 500ms. Returns `null` immediately if `hasDataAtPosition` is
false (ocean, missing tile), avoiding pointless retries.

## createMapHandleRegistry()

A simple `wire`/`unwire` singleton — no event emitter, no multi-subscriber.

```tsx
// Shared singleton (e.g., in a store module):
export const mapRegistry = createMapHandleRegistry();

// React side — wire the handle when the map mounts:
useEffect(() => {
  if (handle) mapRegistry.wire(handle);
  return () => mapRegistry.unwire();
}, [handle]);

// Non-React side — use in thunks, services, etc.:
const h = mapRegistry.requireHandle();   // throws if not wired
const h = mapRegistry.getHandle();       // null if not wired

await enrichCoordinatesWithElevation(coords, h);
```

### Methods

| Method | Returns | Description |
|---|---|---|
| `wire(nativeNodeHandle)` | `void` | Wire (or re-wire) the registry with a new native node handle |
| `unwire()` | `void` | Release the handle (sets internal ref to null) |
| `getHandle()` | `ReturnType<typeof createMapHandle> \| null` | Nullable — use when the caller can degrade gracefully |
| `requireHandle()` | `ReturnType<typeof createMapHandle>` | Throws if not yet wired — use when a handle is required |

## See also

- **[useMap()](../hooks/use-map.md)** — React hook (adds `getDebugLayerDump`)
- **[enrichCoordinatesWithElevation()](./enrich-coordinates.md)** — Batch elevation enrichment (accepts `ElevationAPI` from any source)
- **[MapContainer](../components/map-container.md)** — Root map view and state-lifting pattern
