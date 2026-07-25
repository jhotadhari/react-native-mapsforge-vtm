# GNSS Track-Recording Filter

Native GNSS position recording with configurable filtering and DEM altitude
resolution. Configured via two `MapContainer` props — set `gnssFilter` to
start recording, clear it to stop.

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
      setTrack((prev) => [...prev, e.nativeEvent]);
    },
    []
  );

  return (
    <MapContainer
      gnssFilter={recording ? filter : null}
      onGnssPosition={recording ? handleGnssPosition : undefined}
    >
      {/* layers */}
    </MapContainer>
  );
};
```

## Props

### `gnssFilter: GnssFilterNativeProps | null`

Set to a config object to start recording; set to `null` to stop.
Toggling between different configs stops and restarts the listener.

| Field | Type | Default | Description |
|---|---|---|---|
| `minDistanceMeters` | `number` | `5` | Minimum distance (m) between updates. Passed directly to `LocationManager.requestLocationUpdates` |
| `minTimeSec` | `number` | `2` | Minimum time (s) between updates. Converted to ms internally |
| `minAccuracyMeters` | `number` | `20` | Accuracy guard — positions with accuracy > this are dropped |
| `provider` | `'satellite' \| 'network'` | `'satellite'` | Android location provider. `'satellite'` → `GPS_PROVIDER`, `'network'` → `NETWORK_PROVIDER` |
| `altitudeSource` | `'dem-only' \| 'gnss-only' \| 'dem-preferred' \| 'gnss-preferred'` | `'dem-preferred'` | How altitude is resolved (see below) |

### `onGnssPosition: (e: { nativeEvent: GnssPosition }) => void`

Called on each qualifying position. The `GnssPosition` payload:

| Field | Type | Description |
|---|---|---|
| `lng` | `number` | Longitude in degrees |
| `lat` | `number` | Latitude in degrees |
| `altitude` | `number \| null` | Resolved altitude in metres, or `null` |
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
  `GnssManager.start()` — registers an Android `LocationListener` on the
  main looper. Idempotent.
- **Stop**: clearing `gnssFilter` (setting to `null`) calls
  `GnssManager.stop()` — unregisters the listener. Idempotent.
- **Tear down**: `MapFragment.onDestroy()` stops the listener if active.
- **Permissions**: on `SecurityException` (missing `ACCESS_FINE_LOCATION`),
  the manager emits an error through `onGnssPosition` with `errorMsg` set
  and `lng`/`lat` = 0.

## Platform notes

- **Android only** — iOS codegen stubs exist but `GnssManager` is pure Java.
- Requires `ACCESS_FINE_LOCATION` permission (for GPS provider) or
  `ACCESS_COARSE_LOCATION` (for network provider).
- Position accuracy depends on the device's GNSS chip, sky visibility, and
  the configured `minAccuracyMeters` guard.

## See also

- **[MapContainer](../components/map-container.md)** — All props including `gnssFilter` / `onGnssPosition`
- **[ElevationReader](#)** — Native elevation infrastructure (two-level LRU cache)
- **[enrichCoordinatesWithElevation()](./enrich-coordinates.md)** — Batch post-hoc elevation enrichment
