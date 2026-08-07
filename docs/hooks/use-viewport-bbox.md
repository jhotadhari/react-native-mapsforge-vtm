# useViewportBbox()

Computes and returns a **tile-snapped** viewport bounding box, updating
only when the snapped bbox actually changes (stable-key dedup).

```tsx
import { useViewportBbox } from 'react-native-mapsforge-vtm';
import type { ViewportBbox } from 'react-native-mapsforge-vtm';
```

## Basic usage

```tsx
import { useRef } from 'react';
import {
  MapContainer,
  useViewportBbox,
} from 'react-native-mapsforge-vtm';
import type { MapEventResponse } from 'react-native-mapsforge-vtm';

const SpatialQueryOverlay = () => {
  const eventRef = useRef<MapEventResponse | null>(null);
  const bbox = useViewportBbox(eventRef, 250);

  // bbox is null initially, then [west, south, east, north]
  // Only changes on significant pans (across ~150 km tile boundaries at defaults).
  useEffect(() => {
    if (bbox) fetchDataForBbox(bbox);
  }, [bbox]);

  return (
    <MapContainer
      onMapUpdate={(e) => { eventRef.current = e.nativeEvent; }}
    >
      {/* layers */}
    </MapContainer>
  );
};
```

## Signature

```tsx
function useViewportBbox(
  eventRef: React.MutableRefObject<MapEventResponse | null | undefined>,
  intervalMs: number,
  opts?: {
    snapZoomOffset?: number;  // default 4
    minSnapZoom?: number;     // default 0
    maxSnapZoom?: number;     // default 8
  }
): ViewportBbox | null;
```

## Tile snapping

The key insight: spatial queries don't need pixel-perfect bbox updates on
every tiny pan. Tile snapping means the bbox only changes when the user
pans across a coarse tile boundary (~150 km at defaults):

1. Projects the four screen corners to geographic coordinates via
   `computeViewportBbox` (bearing + tilt aware).
2. Computes the snap tile-zoom: `clamp(floor(mapZoom - snapZoomOffset), minSnapZoom, maxSnapZoom)`.
3. Snaps the bbox to tile boundaries at that zoom via `snapBboxToTiles`.
4. Compares the snapped bbox key to the previous one — only calls `setState`
   when they differ.

| Option | Default | Description |
|---|---|---|
| `snapZoomOffset` | `4` | Subtracted from map zoom to get tile-zoom. Zoom 12 → tile-zoom 8 (~150 km tiles) |
| `minSnapZoom` | `0` | Floor for tile-zoom |
| `maxSnapZoom` | `8` | Ceiling for tile-zoom |

## Return value

Returns `null` initially (before the first map event arrives), but retains
the last valid bbox during transient invalid states (e.g., zero-size
viewport during layout transitions) — no flickering.

## When to use

- Spatial queries (fetch items visible in the current viewport)
- Data-loading triggers that shouldn't fire on every frame
- DB fetches keyed by coarse geographic area
- Any case where you'd otherwise write `useMapEventInterval` +
  `computeViewportBbox` + manual dedup yourself

## See also

- **[useMapEventInterval()](./use-map-event-interval.md)** — The building block this hook is built on
- **[Mercator Math](../api/mercator-math.md)** — `computeViewportBbox`, `snapBboxToTiles`
- **[MapContainer](../components/map-container.md)** — `onMapUpdate` event prop
