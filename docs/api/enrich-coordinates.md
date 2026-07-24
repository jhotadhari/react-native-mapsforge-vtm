# enrichCoordinatesWithElevation()

Batch elevation enrichment for coordinate arrays using SRTM HGT data.
Groups coordinates by 1°×1° tile, preloads tiles in windows, and writes
elevation into every coordinate's third element (`c[2]`).

```tsx
import { enrichCoordinatesWithElevation } from 'react-native-mapsforge-vtm';
import type { ElevationAPI } from 'react-native-mapsforge-vtm';
```

## Basic usage

```tsx
import { useCallback } from 'react';
import {
  useMap,
  enrichCoordinatesWithElevation,
} from 'react-native-mapsforge-vtm';
import type { ElevationAPI } from 'react-native-mapsforge-vtm';

const MyComponent = () => {
  const {
    getAltitudeAtPosition,
    hasDataAtPosition,
    setCacheCapacity,
    isTileCached,
  } = useMap();

  const enrich = useCallback(async (coords: number[][]) => {
    // Ensure every coordinate has a third element for elevation
    for (const c of coords) c[2] = 0;

    const api: ElevationAPI = {
      getAltitudeAtPosition: async (lng, lat) => {
        try { return await getAltitudeAtPosition(lng, lat); }
        catch { return null; }
      },
      hasDataAtPosition: async (lng, lat) => {
        try { return await hasDataAtPosition(lng, lat); }
        catch { return false; }
      },
      setCacheCapacity: async (cap) => { await setCacheCapacity(cap); },
      isTileCached: async (lng, lat) => {
        try { return await isTileCached(lng, lat); }
        catch { return false; }
      },
    };

    await enrichCoordinatesWithElevation(coords, api, {
      onProgress: (f) => console.log(`${Math.round(f * 100)}%`),
      maxCacheCapacity: 50,
    });

    // coords now enriched — c[2] contains elevation in metres
  }, [getAltitudeAtPosition, hasDataAtPosition, setCacheCapacity, isTileCached]);

  return /* ... */;
};
```

## ElevationAPI

The bridge interface the library needs to perform enrichment. Wire it from
`useMap()` as shown above — each method maps directly to a `useMap()` bridge
function, with the error-handling strategy described below.

| Method | Throws? | Purpose |
|---|---|---|
| `getAltitudeAtPosition(lng, lat)` | Catches → `null` | Get bilinearly-interpolated elevation in metres. Triggers a background preload on cache miss — the library handles all retry/fencing logic, so pass the raw bridge function, **not** a retry-wrapped version. |
| `hasDataAtPosition(lng, lat)` | Catches → `false` | Returns `true` if an HGT file exists covering the position, regardless of whether its data is cached. No I/O, no preload. When `false` the tile is ocean/missing — the library skips it entirely. |
| `isTileCached?(lng, lat)` | Catches → `false` | Returns `true` if the HGT tile is currently in the LRU cache. Never triggers a preload. Unlike `getAltitudeAtPosition` this is unambiguous — `false` always means "not cached", never "cached but void pixel". Optional but strongly recommended; the fence falls back to `getAltitudeAtPosition` sampling without it. |
| `setCacheCapacity?(capacity)` | **Throws** | Resize the native LRU cache. Optional — when absent the window size is capped at the compile-time default (10 tiles). Must **throw** on failure (config command, must not fail silently). |

## EnrichCoordinatesOptions

| Option | Default | Description |
|---|---|---|
| `maxCacheCapacity` | `50` | Maximum HGT tiles held in the LRU cache during enrichment. At ~2.9 MB per SRTM3 tile this caps RAM usage at roughly 145 MB on top of the app's baseline. |
| `keepCacheCapacity` | `false` | When `true` the elevated cache capacity is NOT restored to the compile-time default (10) after enrichment. Use when you expect to run enrichment again soon — keeping tiles warm avoids redundant disk reads. |
| `onProgress` | — | Called after each window completes with the overall fraction (0–1). Suitable for driving a progress bar. |
| `signal` | — | `AbortSignal`. When aborted, enrichment stops at the next window boundary. Coordinates enriched so far are preserved — it does not roll back. |

## Return value

Mutates `coords` **in place** (sets `c[2]` on every coordinate) and returns the
same array for chaining. Coordinates in tiles without HGT coverage (ocean,
missing data) are left unchanged.

## How it works

The function uses a windowed three-phase flow so the operation works correctly
even when the native LRU cache is smaller than the tile set:

1. **Group** — coordinates are grouped by 1°×1° SRTM tile. Tiles without HGT
   coverage are filtered out via `hasDataAtPosition`.
2. **Cache sizing** — the LRU cache is optionally raised to `maxCacheCapacity`
   (or capped at the compile-time default of 10 if `setCacheCapacity` is absent).
   The window size equals the effective cache capacity, guaranteeing 100%
   cache-hit rate in the collect phase.
3. **Per-window loop:**
   - **Phase 1 — Trigger:** fire `getAltitudeAtPosition` for every tile in the
     window, queuing background preloads on the native `PRELOAD_EXECUTOR`.
   - **Phase 2 — Fence:** poll every tile via `isTileCached` until all are
     loaded or the adaptive timeout expires. Re‑triggers preloads for
     stragglers. The timeout scales with window size and accounts for I/O
     throughput (~500 ms/tile).
   - **Phase 3 — Collect:** all tiles are guaranteed cached — every
     `getAltitudeAtPosition` call is a sub‑millisecond cache hit.
     `findElevationInTile` handles void pixels gracefully by probing a
     3×3 grid (then a 5×5 sweep for tiles with very little land).
4. **Teardown** — the LRU cache capacity is restored to its compile-time
   default (10) in `finally`, unless `keepCacheCapacity` was set.

## See also

- **[ElevationReader](#)** — Native-side elevation infrastructure (two-level cache, preload executors, debounced preload)
- **[useMap()](../hooks/use-map.md)** — Bridge methods (`getAltitudeAtPosition`, `hasDataAtPosition`, `isTileCached`, `setCacheCapacity`)
