# GNSS Position Streams

Native GNSS position delivery with configurable recording filtering and DEM
altitude resolution. Configured via the `gnssFilter` prop — set it to start
the listener, clear it to stop. Two streams are emitted:

- **`onGnssLive`** — every fix the provider delivers (~1 Hz), guarded only
  against garbage accuracy. Dense even at low speeds; for live UI (map
  marker, speed/course dashboard widgets).
- **`onGnssPosition`** — only positions that qualify for track recording,
  gated natively by `minDistanceMeters` + `minTimeSec` + `minAccuracyMeters`.

```tsx
import type {
  GnssFilterNativeProps,
  GnssPosition,
} from 'react-native-mapsforge-vtm';
```

## Basic usage

```tsx
import { useState, useCallback } from 'react';
import { MapContainer } from 'react-native-mapsforge-vtm';
import type {
  GnssFilterNativeProps,
  GnssPosition,
} from 'react-native-mapsforge-vtm';

const GnssTracker = () => {
  const [track, setTrack] = useState<GnssPosition[]>([]);
  const [live, setLive] = useState<GnssPosition | null>(null);
  const [recording, setRecording] = useState(false);

  const filter: GnssFilterNativeProps = {
    minDistanceMeters: 5,
    minTimeSec: 2,
    minAccuracyMeters: 20,
    provider: 'satellite',
    altitudeSource: 'dem-preferred',
  };

  const handleGnssPosition = useCallback(
    (e: { nativeEvent: GnssPosition }) => {
      // Recording-qualifying position — persist it.
      setTrack((prev) => [...prev, e.nativeEvent]);
    },
    []
  );

  const handleGnssLive = useCallback(
    (e: { nativeEvent: GnssPosition }) => {
      // Every fix — update the live position UI.
      setLive(e.nativeEvent);
    },
    []
  );

  return (
    <MapContainer
      gnssFilter={recording ? filter : null}
      onGnssPosition={recording ? handleGnssPosition : undefined}
      onGnssLive={handleGnssLive}
    >
      {/* layers */}
    </MapContainer>
  );
};
```

## Props

### `gnssFilter: GnssFilterNativeProps | null`

Set to a config object to start the listener; set to `null` to stop.
Toggling between different configs stops and restarts the listener.

| Field | Type | Default | Description |
|---|---|---|---|
| `minDistanceMeters` | `number` | `5` | Minimum distance (m) between **recording** positions |
| `minTimeSec` | `number` | `2` | Minimum time (s) between **recording** positions |
| `minAccuracyMeters` | `number` | `20` | Accuracy guard — positions with accuracy > this never qualify for recording |
| `provider` | `'satellite' \| 'network'` | `'satellite'` | Android location provider. `'satellite'` → `GPS_PROVIDER`, `'network'` → `NETWORK_PROVIDER` |
| `altitudeSource` | `'dem-only' \| 'gnss-only' \| 'dem-preferred' \| 'gnss-preferred'` | `'dem-preferred'` | How altitude is resolved (see below) |

> The listener is registered with **raw parameters** (`0` ms / `0` m) so the
> live stream never thins to the recording cadence. `minDistanceMeters` and
> `minTimeSec` only gate the `onGnssPosition` recording stream.

### `onGnssPosition: (e: { nativeEvent: GnssPosition }) => void`

Called on each recording-qualifying position: accuracy within
`minAccuracyMeters`, **and** at least `minTimeSec` elapsed **and**
`minDistanceMeters` moved since the last qualifying position. This is the
stream to persist (track recording).

### `onGnssLive: (e: { nativeEvent: GnssPosition }) => void`

Called on **every** fix the provider delivers (~1 Hz), regardless of the
recording filter. Guarded only against garbage accuracy — the weaker of a
100 m cap and `minAccuracyMeters` (never stricter than the recording gate,
so the recording stream cannot starve). Use this for live UI: the map
marker, speed/course widgets, live altitude.

Both streams share the `GnssPosition` payload:

| Field | Type | Description |
|---|---|---|
| `lng` | `number` | Longitude in degrees |
| `lat` | `number` | Latitude in degrees |
| `altitude` | `number \| null` | Resolved altitude in metres, or `null` |
| `gnssAltitude` | `number \| null` | Raw GNSS altitude, independent of `altitudeSource` |
| `bearing` | `number` | Bearing in degrees, or `-1` if unavailable |
| `accuracy` | `number` | Accuracy in metres |
| `speed` | `number` | Speed in m/s, or `0` if unavailable |
| `timestamp` | `number` | Unix epoch milliseconds |

## Altitude resolution

The `altitudeSource` field controls how `altitude` is resolved:

| Source | Behavior |
|---|---|
| `dem-only` | Always uses DEM (SRTM HGT) altitude. Returns `null` if no DEM data. |
| `gnss-only` | Always uses the GNSS chip's altitude (often poor accuracy). |
| `dem-preferred` | Uses DEM altitude if available, falls back to GNSS altitude, then `null`. **Default.** |
| `gnss-preferred` | Uses GNSS altitude if available, falls back to DEM altitude, then `null`. |

DEM altitude is resolved via `ElevationReader` — a **fast-path cache hit**.
On cache miss, `ElevationReader` returns `null` immediately and triggers a
background preload; the next location update (~1–2 s later) gets the cached
value. No blocking retry loop on the main thread.

The `ElevationReader` is the same instance used by `getAltitudeAtPosition`
and `enrichCoordinatesWithElevation` — all share the same LRU cache.

## Lifecycle

- **Start**: setting `gnssFilter` on a mounted `MapContainer` calls
  `GnssManager.start()` — registers an Android `LocationListener` (raw
  parameters) on the main looper. Idempotent.
- **Stop**: clearing `gnssFilter` (setting to `null`) calls
  `GnssManager.stop()` — unregisters the listener. Idempotent.
- **Tear down**: `MapFragment.onDestroy()` stops the listener if active.
- **Permissions**: on `SecurityException` (missing `ACCESS_FINE_LOCATION`),
  the manager emits an error through **both** streams with `errorMsg` set
  and `lng`/`lat` = 0.

## Platform notes

- **Android only** — iOS codegen stubs exist but `GnssManager` is pure Java.
- Requires `ACCESS_FINE_LOCATION` permission (for GPS provider) or
  `ACCESS_COARSE_LOCATION` (for network provider).
- Position accuracy depends on the device's GNSS chip, sky visibility, and
  the configured `minAccuracyMeters` guard.

## See also

- **[MapContainer](../components/map-container.md)** — All props including `gnssFilter` / `onGnssPosition` / `onGnssLive`
- **[ElevationReader](#)** — Native elevation infrastructure (two-level LRU cache)
- **[enrichCoordinatesWithElevation()](./enrich-coordinates.md)** — Batch post-hoc elevation enrichment
