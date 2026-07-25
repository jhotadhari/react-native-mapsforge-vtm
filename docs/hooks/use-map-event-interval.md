# useMapEventInterval()

Polls a map-event ref at a fixed interval, calling a callback with the
latest `MapEventResponse` (or `null` if no event has arrived yet).

```tsx
import { useMapEventInterval } from 'react-native-mapsforge-vtm';
```

## Basic usage

```tsx
import { useRef } from 'react';
import { useMapEventInterval } from 'react-native-mapsforge-vtm';
import type { MapEventResponse } from 'react-native-mapsforge-vtm';

const MapTracker = () => {
  const eventRef = useRef<MapEventResponse | null>(null);

  useMapEventInterval(eventRef, 250, (event) => {
    if (event) {
      console.log(event.center, event.zoomLevel);
    }
  });

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
function useMapEventInterval(
  eventRef: React.MutableRefObject<MapEventResponse | null | undefined>,
  intervalMs: number,
  callback: (event: MapEventResponse | null) => void
): void;
```

## How it works

- Sets up a `setInterval` that reads `eventRef.current` on each tick.
- Uses a **callback-ref** internally — the interval is NOT re-registered when
  the callback identity changes (e.g., a new closure on every render). Only an
  `intervalMs` change restarts the timer.
- `eventRef` itself is a `MutableRefObject` — its identity is stable across
  renders, so it isn't in the dependency array.

This means you can safely inline a callback that captures state without
causing the interval to churn:

```tsx
const [bbox, setBbox] = useState<ViewportBbox | null>(null);

useMapEventInterval(eventRef, 250, (event) => {
  // This closure captures the latest `bbox` via the callback-ref.
  // The interval itself keeps running with the same timer.
  if (event) {
    const newBbox = computeViewportBbox(/* ... */);
    setBbox(newBbox);
  }
});
```

## When to use

Use when you need periodic reads of the map state at a known frequency —
spatial queries, viewport tracking, data loading triggers. This is the
building block that `useViewportBbox` is built on.

For **reactive** (not periodic) map updates, use the `onMapUpdate` callback
directly on `MapContainer`.

## See also

- **[useViewportBbox()](./use-viewport-bbox.md)** — Tile-snapped viewport bbox built on this hook
- **[useMapPosition()](../advanced/performance.md)** — Reanimated shared values for 60fps position tracking
- **[MapContainer](../components/map-container.md)** — `onMapUpdate` event prop
