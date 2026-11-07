# GNSS Position Streams

Native GNSS position delivery with configurable recording filtering and DEM
altitude resolution. Owned by the application-scoped **`GnssModule`**
TurboModule — configure it with `setFilter()`, stop it with
`setFilter(null)`. The listener is **not tied to any map view**: it keeps
running while no surface is mounted, which is what makes background track
recording work. Two streams are emitted as device events:

- **`onGnssLive`** — every fix the provider delivers (~1 Hz), guarded only
  against garbage accuracy. Dense even at low speeds; for live UI (map
  marker, speed/course dashboard widgets).
- **`onGnssPosition`** — only positions that qualify for track recording,
  gated natively by `minDistanceMeters` + `minTimeSec` + `minAccuracyMeters`.

```tsx
import GnssModule, {
  type GnssFilterNativeProps,
  type GnssPosition,
} from 'react-native-mapsforge-vtm';
```

## Basic usage

```tsx
import { useEffect, useState } from 'react';
import { DeviceEventEmitter } from 'react-native';
import GnssModule, {
  type GnssFilterNativeProps,
  type GnssPosition,
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

  useEffect(() => {
    const onLive = DeviceEventEmitter.addListener('onGnssLive', (p: GnssPosition) => {
      setLive(p);
    });
    const onPosition = DeviceEventEmitter.addListener('onGnssPosition', (p: GnssPosition) => {
      setTrack((prev) => [...prev, p]);
    });
    return () => {
      onLive.remove();
      onPosition.remove();
    };
  }, []);

  useEffect(() => {
    // Start/stop the application-scoped listener. It keeps running
    // after the component unmounts (e.g. background recording) until
    // setFilter(null) is called.
    GnssModule.setFilter(recording ? filter : null);
  }, [recording]);

  return null; // or the map UI — GNSS no longer needs a MapContainer
};
```

## Module API

| Method | Description |
|---|---|
| `setFilter(filter \| null)` | Configure or stop the listener. `null` stops; a config (re)starts it. Reconfiguring restarts the listener with the new gate. |
| `setHgtDirPath(path \| null)` | Configure the DEM directory used for altitude resolution (same semantics as the map's `setHgtDirPath`). `null`/empty tears the reader down — altitude then falls back to raw GNSS. |

## Filter config

The `GnssFilterNativeProps` object passed to `setFilter`:

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

## Streams

### `onGnssPosition`

Called on each recording-qualifying position: accuracy within
`minAccuracyMeters`, **and** at least `minTimeSec` elapsed **and**
`minDistanceMeters` moved since the last qualifying position. This is the
stream to persist (track recording).

### `onGnssLive`

Called on **every** fix the provider delivers (~1 Hz), regardless of the
recording filter. Guarded only against garbage accuracy — the weaker of a
100 m cap and `minAccuracyMeters` (never stricter than the recording gate,
so the recording stream cannot starve). Use this for live UI: the map
marker, speed/course widgets, live altitude.

Both streams share the `GnssPosition` payload (device events, not view
events):

| Field | Type | Description |
|---|---|---|
| `lng` | `number` | Longitude in degrees |
| `lat` | `number` | Latitude in degrees |
| `altitude` | `number \| null` | Resolved altitude in metres, or `null` |
| `gnssAltitude` | `number \| null` | Raw GNSS altitude, independent of `altitudeSource` |
| `elevationSource` | `'dem' \| 'gnss' \| 'none'` | Which source actually resolved `altitude` — `'none'` when `altitude` is `null` |
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

- **Start**: `setFilter(config)` calls `GnssManager.start()` — registers an
  Android `LocationListener` (raw parameters) on the main looper.
  Idempotent.
- **Stop**: `setFilter(null)` calls `GnssManager.stop()` — unregisters the
  listener. Idempotent.
- **App scope**: the module (and its listener) is owned by the application,
  **not** by any map view — it keeps running after the surface is gone
  (background track recording). The process itself must stay alive (e.g. a
  location-type foreground service in the host app).
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

- **[MapContainer](../components/map-container.md)** — GNSS is no longer a
  MapContainer concern (see the GNSS module section there)
- **[ElevationReader](#)** — Native elevation infrastructure (two-level LRU cache)
- **[enrichCoordinatesWithElevation()](./enrich-coordinates.md)** — Batch post-hoc elevation enrichment
