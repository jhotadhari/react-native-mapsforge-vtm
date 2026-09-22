# AGENTS.md — native side (`android/`)

Layer-mutation and rendering native code for `react-native-mapsforge-vtm`.

## Layout

Under `android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/`:

- `modules/` — one hand-written class per layer/map-container (`LayerMarker.java`, `MapContainer.java`,
  etc.), each extending its codegen-generated `NativeXxxSpec` base class.
- `views/` — `MapFragment` (the vtm `MapView` host), `MapsforgeVtmView`, `MapsforgeVtmViewManager`
  (implements the codegen-generated Fabric manager interface), `VtmAnchorView` + `VtmAnchorViewManager`
  (the invisible anchor host component the committed-tree walk reads).
- `layer/` — custom vtm layer subclasses and shared-layer infrastructure: `PathLayer`, `VectorLayer`,
  `ItemizedLayer`, `GestureLayer` (vtm layer subclasses); `LayerManager` (base class for shared-layer
  managers — owns `ensureSharedLayer()`, entry lifecycle, gesture delegation); `PathLayerJtsWrapper`
  (`LayerPathJts`-specific vtm `PathLayer` wrapper).
- `gnss/` — `GnssManager` (Android `LocationListener` wrapper; see below).
- Top-level shared-layer managers (one per layer type using shared architecture):
  `PathLayerManager extends LayerManager<PathEntry>`, `MarkerLayerManager extends LayerManager<MarkerEntry>`,
  `ShapeLayerManager extends LayerManager<ShapeEntry>`. Each manages entries (drawables/markers) within
  shared `VectorLayer`/`ItemizedLayer` instances, handles hit-testing, ZOMBIE diagnostics, and
  per-entry `scheduleUpdate()` coalescing.
- Top-level helpers: `ElevationReader`/`Utils` (elevation lookups from `.hgt` DEM files), `LayerHelper`/
  `LayerZoomBoundsHelper` (zoom-based layer visibility), `RenderThemeMenuLoader` (parses
  `<stylemenu>` from render-theme XML for `useRenderStyleOptions`). `MapFragment` emits map events on
  every vtm frame with no rate limiting.
- `android/strip-vtm-classes.gradle` — reusable Gradle script that strips shadowed vtm classes
  at multiple stages to prevent "Type X is defined multiple times" errors: (1) patches the vtm
  JAR in the Gradle cache (`zip -d` with crash recovery + automatic post-build restore so the
  shared cache is never left broken), (2) at configuration time, excludes shadowed `.java`
  source files from the core library's compilation via Gradle's `SourceDirectorySet.exclude()`
  (non-destructive — no files are deleted from disk), and (3) at execution time, cleans stale
  `.class`/`.dex` build artifacts and the `runtime_library_classes_jar` directory from the core
  library's build tree before DEX merging. Applied via `apply from:` in the consuming app's
  `android/app/build.gradle`; extensions declare `ext.shadowedClasses` in their own `build.gradle`.

## Threading model

All mutations to `mapView.map().layers()` (add, remove, reorder) flow through
`MapMutationQueue.flush()` on the **UI thread** (Main Looper), which delegates the actual
`layers()` mutation to `LayerStackController` (`removeAll`, `applyPlan` → `reorderMinimalMoves`,
`removeLayerSync`).

Two **sanctioned exceptions** bypass the queue:

1. **`removeLayerSync()`** — used during teardown (`LayerManager.destroy()`), when the async
   flush may never run. UI-thread only.
2. **`MapFragment`'s internal `GestureLayer`** — added/removed directly (it's a vtm-internal
   layer, not JS-managed).

```
 Native Modules Thread (TurboModule)          UI Thread (Main Looper)
 ================================             =======================

 createLayer (async)  ──enqueue──>  ┌─────────────────────────────┐
 removeLayer (async)  ──enqueue──>  │  MapMutationQueue.flush()    │
 reorderLayers        ──enqueue──>  │  ─────────────────────────  │
                                     │  1. Remove stale layers      │
 animateTo()          ──dispatch──> │  2. Add new layers (append)  │
 getPosition()        ──dispatch──> │  3. Apply absolute plan (LIS)│
                                     │  4. updateMap() once         │
 scheduleUpdate()     ──post─────>  │  updateMap() coalesced       │
 (LayerManager +       (CAS+Handler)│  (per-entry geometry changes)  │
  Marker/Path/Shape)                 └─────────────────────────────┘

 LayerManager.ensureSharedLayer() blocks with future.get()
 until the shared layer is placed on the UI thread — this is
 the ONLY cross-thread blocking wait. Everything else is
 fire-and-forget from the JS side.
```

**Key rules:**
- `MapMutationQueue.flush()` (delegating to `LayerStackController`) is the only runtime place that
  calls `layers().add/remove` and the batch-level `updateMap()`. Adds are **appended** (each new
  entry uses the `APPEND_PRIORITY` placeholder); their final position comes from the absolute plan
  (`LayerStackController.applyPlan`) applied in the same flush. `reorderLayers` uses
  `reorderMinimalMoves` (LIS); when the first element of the target order is not in the LIS it
  inserts before the first existing JS-managed layer (not index 0, which would push layers before
  vtm-internal layers like GestureLayer).
- `MapFragment.bindUpdateListener()` runs on the **render thread** (vtm GL, 60fps) and writes
  position data to C++ `Synchronizable` primitives via `MapPositionWriter.nativeSetPosition()`
  (thread-safe mutex). See `android/src/main/cpp/MapPositionWriter.cpp`.
- `scheduleUpdate()` (in `LayerManager` + per-type subclasses) coalesces per-entry `updateMap()`
  onto the UI thread via `AtomicBoolean` CAS + `Handler.post`.
- `MapContainer.animateTo()` / `getPosition()` dispatch to the UI thread via `UiThreadUtil.runOnUiThread`.
- `MapFragment.onDestroy()` must tear down all `LayerManager` instances **and** `GnssManager`
  (`gnssManager.stop()`) **before** `mapView.onDestroy()`, or shared layers silently leak and the
  GNSS listener continues firing into a dead map.
- `LayerHelper.addLayerAsync` / `removeLayerAsync` are the API — they enqueue into
  `MapMutationQueue`. The old synchronous `addLayer`/`removeLayer` methods were removed (breaking change).

**Read-only / non-layers-mutating on the native-modules thread:**
- `LayerHelper.getLayer()` / `getLayers()` — reads `MapMutationQueue.getKnownLayers()` (ConcurrentHashMap).
- `LayerZoomBoundsHelper.removeUpdateListener()` — `mapView.map().events.unbind()`.
- Marker/Path/Shape entry creation — operates on already-registered shared `Layer` objects
  (adds drawables/markers to `VectorLayer`/`ItemizedLayer`), not on `map.layers()`.

## Shared-layer managers

### Marker batching

`MarkerBatchQueue` (`src/compose/MarkerBatchQueue.ts`) collapses N `createMarker`/`removeMarker`
calls into 1 `createMarkers` + 1 `removeMarkers` batch call, flushed on the microtask boundary
(`Promise.resolve().then()`) with a 16ms safety max-wait `setTimeout`. Call
`drainQueue(nativeNodeHandle)` on map destruction to reject pending operations.

### Marker sort direction

Markers inside a shared `ItemizedLayer` fragment are ordered by **ascending** `positionIndex`
(lower z first); equal priorities break by **ascending `creationSeq`** — a monotonic
sequence (`AtomicLong`) assigned in source order at validation time. `applyEntryPriorities`
rebuilds the item list in this order (sort outside the layer lock, then a single O(n) swap).
`positionIndex` is a sparse priority from the JS `PriorityAllocator`, applied via
`applyEntryPriorities` (not a create-time param); new markers append and are re-ordered on the
next `applyEntryPriorities`.

Path/Shape managers use `VectorLayer`, which sorts by `getPriority()` ascending — no tie-break.

### ZOMBIE diagnostics

A deliberate, systematic defensive pattern that prevents "zombie" native resources — GPU objects
(drawables, layers) that outlive their JS-side references (teardown races where the map is
destroyed while async operations are in-flight).

The three managers (`PathLayerManager`, `MarkerLayerManager`, `ShapeLayerManager`) log
`"ZOMBIE: ..."` **warnings** when they detect an operation targeting an already-destroyed shared
layer. These are expected during teardown, not errors. When a zombie is detected, the manager
clears its drawable references so the GC can collect them (the drawables remain in the
`VectorLayer`'s QuadTree and render as zombies until the shared layer itself is destroyed).

The JS side (`useNativeLayerLifecycle`'s `uuidRef`, `SceneSync.destroy()`) prevents zombies from
being created in the first place — see the root `AGENTS.md`.

## ElevationReader

Provides elevation lookups from `.hgt` DEM files, with a two-level cache: an in-memory `dataCache`
(`LruCache`) and a filename index (`fileIndex`, built once at construction). Three executors/threads:

| Thread | Role |
|---|---|
| **Render thread** (vtm GL, 60fps) | `MapFragment.getResponseBase()` calls `getElevation(lng, lat, 100)` (100ms-debounced overload — never blocks on I/O). |
| **`PRELOAD_EXECUTOR`** (4-thread fixed pool) | Immediate `preload()` (2.9MB `.hgt` reads), used by the JS `getAltitudeAtPosition` API (delay=0). |
| **`DELAYED_PRELOAD_EXECUTOR`** (single-thread scheduled) | Debounced preloads from the render hot-path. |

**Debounced preload (single-slot):** `schedulePreload()` holds at most one pending delayed preload;
a **different** tile cancels the prior, a **same** tile leaves the timer running.

**In-flight dedup:** `inFlightPreloads` prevents duplicate `preload()` tasks per filename.

**`hasDataFor(lng, lat)`:** true if the tile index contains the coordinate — fast, thread-safe, no
I/O. Exposed to JS as `hasDataAtPosition`.

Native additions supporting elevation enrichment: `isTileCached(lng, lat)` (direct `LruCache.get()`,
never preloads) and `setCacheCapacity(int maxTiles)` (`LruCache.resize()`).

## GNSS track-recording filter

`GnssManager` (`android/…/gnss/GnssManager.java`) wraps Android's `LocationManager` to record GNSS
positions through a configurable filter, controlled by two `MapContainer` props: `gnssFilter`
(starts/stops recording) and `onGnssPosition` (payload callback).

Filter config: `minDistanceMeters` (~5m default), `minTimeSec` (~2s default), `minAccuracyMeters`
(~20m default), `provider` (`'satellite'`/`'network'`), `altitudeSource`
(`'dem-only'`/`'gnss-only'`/`'dem-preferred'`(default)/`'gnss-preferred'`).

Altitude: when not `'gnss-only'`, `GnssManager` calls `ElevationReader.getElevation` for DEM altitude
(fast cache hit; miss → null + background preload, no blocking retry). `MapFragment.updateGnssFilter()`
manages lifecycle; `onDestroy()` stops the listener; `start()` is idempotent and handles
`SecurityException` by emitting an error through the callback.
