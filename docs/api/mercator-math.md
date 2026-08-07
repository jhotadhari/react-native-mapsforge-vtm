# Mercator Math Utilities

Non-worklet Mercator projection utilities. Same math as the worklet versions
in `react-native-mapsforge-vtm/reanimated` (same `TILE_SIZE` = 576, same
density handling), callable from React hooks, event handlers, and any
non-worklet JS context.

```tsx
import {
  latLngToMercator,
  toScreenPosition as geoToScreenPosition,
  computeViewportBbox,
  snapBboxToTiles,
} from 'react-native-mapsforge-vtm';
```

## Core projection

### `clampLat(lat: number): number`

Clamps a latitude to the Web Mercator valid range `[-85.051129°, +85.051129°]`.

```tsx
clampLat(90); // 85.0511287798066
clampLat(-90); // -85.0511287798066
```

### `latLngToMercator(lat: number, lng: number): { mx: number; my: number }`

Converts geographic lat/lng to normalised Mercator coordinates.
`mx ∈ [0, 1]` (0 = antimeridian/-180°), `my ∈ [0, 1]` (0 = north pole).

Longitude is auto-wrapped — 190° maps to the same mx as -170°.

### `mercatorToLatLng(mx: number, my: number): { lat: number; lng: number }`

Inverse of `latLngToMercator` — normalised Mercator → geographic.

## Antimeridian wrapping

### `wrapLngDelta(dLng: number): number`

Wraps a longitude difference to `[-180°, +180°]`. Useful when computing
deltas across the antimeridian.

### `wrapMxDelta(dMx: number): number`

Wraps a normalised Mercator mx delta to `[-0.5, +0.5]`.
Used internally by `toScreenPosition` / `fromScreenPosition`.

## Screen ↔ geographic conversion

### `toScreenPosition(center, zoom, viewportWidth, viewportHeight, bearing, tilt, geoPoint, options?): { x: number; y: number } | null`

Converts a geographic point to screen pixel coordinates (dp).
Bearing and tilt are fully accounted for. Returns `null` on invalid input
(zero/negative zoom, zero viewport, missing center).

```tsx
const screenPos = toScreenPosition(
  [13.405, 52.52], // center [lng, lat]
  14,               // zoom
  400, 600,         // viewportWidth, viewportHeight
  45,               // bearing (degrees)
  30,               // tilt (degrees)
  { lat: 52.53, lng: 13.42 }  // point to project
);
// { x: 250, y: 180 }
```

### `fromScreenPosition(center, zoom, viewportWidth, viewportHeight, bearing, tilt, screenPoint, options?): { lat: number; lng: number } | null`

Inverse of `toScreenPosition` — screen pixel → geographic coordinate.
Bearing and tilt are fully accounted for.

```tsx
const geoPos = fromScreenPosition(
  [13.405, 52.52], 14, 400, 600, 45, 30,
  { x: 250, y: 180 }
);
// { lat: 52.53, lng: 13.42 }
```

### `MercatorMathOptions`

| Option | Default | Description |
|---|---|---|
| `tileSize` | `576` (vtm's `Tile.SIZE`) | Tile size in pixels. Use `256` for standard Web Mercator / OSM tiles |
| `density` | `PixelRatio.get()` at module init | Device pixel ratio |

## Viewport bounding box

### `computeViewportBbox(center, zoom, viewportWidth, viewportHeight, bearing, tilt, options?): ViewportBbox | null`

Computes the axis-aligned geographic bounding box of the visible viewport.
Projects the four screen corners to lat/lng and takes the min/max extent.

With bearing/tilt, the true visible area is a rotated trapezoid, so the
returned AABB is larger than the true visible footprint — safe for spatial
culling (may include a few extra off-screen items, but never misses one).

```tsx
const bbox = computeViewportBbox(
  [13.405, 52.52], 14, 400, 600, 0, 0
);
// [13.38, 52.51, 13.43, 52.53] — [west, south, east, north]
```

### `ViewportBbox`

```tsx
type ViewportBbox = [number, number, number, number]; // [west, south, east, north]
```

## Tile-grid helpers

Standard Web Mercator / OSM tile scheme.

### `lngLatToTile(lng: number, lat: number, zoom: number): { x: number; y: number }`

Returns the tile coordinates for a geographic point at the given zoom level.

### `tileToBbox(tx: number, ty: number, zoom: number): ViewportBbox`

Returns the geographic bounding box of a single tile.

### `snapBboxToTiles(bbox: ViewportBbox, tileZoom: number): ViewportBbox`

Snaps a geographic bbox to Web Mercator tile boundaries at `tileZoom`.
Returns the smallest bbox that fully contains the input and is aligned to
tile edges.

Small pans within the same tile(s) produce the same snapped bbox, so query
keys stay stable and no unnecessary work fires on minor pans.

```tsx
const bbox = computeViewportBbox([13.405, 52.52], 14, 400, 600, 0, 0);
// [13.378, 52.514, 13.432, 52.526] — precise viewport bbox

const snapped = snapBboxToTiles(bbox, 10);
// [11.25, 51.399, 14.062, 53.330] — snapped to zoom-10 tile boundaries (~150 km tiles)
// This won't change again until the user pans across a tile boundary.
```

## See also

- **[useViewportBbox()](../hooks/use-viewport-bbox.md)** — React hook combining `computeViewportBbox` + `snapBboxToTiles` with stable-key dedup
- **[Reanimated sub-package](../advanced/performance.md)** — Worklet equivalents (`toScreenPosition`, `fromScreenPosition`)
- **[useMapEventInterval()](../hooks/use-map-event-interval.md)** — Poll map events at a fixed interval
